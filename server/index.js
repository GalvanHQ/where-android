// ── index.js ────────────────────────────────────────────────────────────────
//
// Entry point for the Where chat + location relay server.
//
// Performance + production hardening (see docs/SERVER_PERFORMANCE.md):
//   • Trust proxy headers from Cloud Run for accurate client IPs / scheme.
//   • gzip compression for REST JSON responses.
//   • Helmet security headers (CSP intentionally off — pure JSON API).
//   • CORS allowlist with safe defaults (env: CORS_ORIGINS).
//   • Bearer-token cache (LRU, 1 h TTL) shared with the Socket.IO handshake
//     so each user's ID token is verified at most once per hour across all
//     entry points.
//   • Per-IP rate limiter on REST routes (sane default, can be tuned via env).
//   • Structured request logging with latency and request id.
//   • Graceful shutdown drains in-flight HTTP + open WebSockets on SIGTERM
//     so Cloud Run rolling deploys don't kill chats mid-message.
//   • Tightened HTTP keep-alive timeouts to match Cloud Run's 60s LB.

require('dotenv').config();
const http = require('http');
const { randomUUID } = require('crypto');

const express = require('express');
const cors = require('cors');
const compression = require('compression');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const { Server } = require('socket.io');

const { auth } = require('./src/firebase');
const { verifyIdTokenCached } = require('./src/authCache');
const conversationRoutes = require('./src/routes/conversations');
const directionsRoutes = require('./src/routes/directions');

const app = express();

// ── 1. Proxy + parser plumbing ──────────────────────────────────────────────

// Cloud Run sits behind Google's load balancer. Trusting one hop ahead lets
// req.ip / req.protocol reflect the real caller for rate limiting + logs.
app.set('trust proxy', 1);
app.disable('x-powered-by');
app.disable('etag'); // JSON payloads are cheap; etags add hashing cost without buying caches.

app.use(express.json({ limit: '256kb' }));

// ── 2. Security headers + compression ───────────────────────────────────────

// CSP is disabled because we don't serve HTML; cross-origin everything else.
app.use(
    helmet({
        contentSecurityPolicy: false,
        crossOriginResourcePolicy: { policy: 'cross-origin' },
    })
);

app.use(
    compression({
        // Don't bother compressing tiny chat payloads — Brotli/gzip overhead
        // dominates below ~1 KB. Above that it's a clear win.
        threshold: 1024,
    })
);

// ── 3. CORS allowlist ───────────────────────────────────────────────────────
//
// Production should set CORS_ORIGINS to the exact origins that may call us.
// Native Android requests don't carry an Origin header, so the default of
// "*" stays safe for the mobile client when the env var is unset; teams
// shipping a web client should pin it to their domain(s).

const rawCorsOrigins = (process.env.CORS_ORIGINS || '').trim();
const corsOptions = rawCorsOrigins.length === 0 || rawCorsOrigins === '*'
    ? {
        origin: true,
        methods: ['GET', 'POST', 'PATCH', 'DELETE'],
        credentials: false,
    }
    : {
        origin: rawCorsOrigins.split(',').map((o) => o.trim()).filter(Boolean),
        methods: ['GET', 'POST', 'PATCH', 'DELETE'],
        credentials: false,
    };
app.use(cors(corsOptions));

// ── 4. Request id + structured access log ───────────────────────────────────

app.use((req, res, next) => {
    const reqId = req.headers['x-cloud-trace-context']?.split('/')[0] || randomUUID();
    req.id = reqId;
    res.setHeader('X-Request-Id', reqId);

    const startedAt = process.hrtime.bigint();
    res.on('finish', () => {
        // Skip the noisy health check
        if (req.path === '/' || req.path === '/healthz') return;
        const elapsedMs = Number(process.hrtime.bigint() - startedAt) / 1_000_000;
        // Single-line JSON keeps Cloud Logging happy.
        process.stdout.write(
            JSON.stringify({
                severity: res.statusCode >= 500 ? 'ERROR' : 'INFO',
                reqId,
                method: req.method,
                path: req.path,
                status: res.statusCode,
                latencyMs: Number(elapsedMs.toFixed(1)),
                uid: req.user?.uid || null,
            }) + '\n'
        );
    });
    next();
});

// ── 5. REST auth ────────────────────────────────────────────────────────────

app.use(async (req, res, next) => {
    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
        const token = authHeader.slice(7);
        const decoded = await verifyIdTokenCached(auth, token);
        if (decoded) req.user = decoded;
    }
    next();
});

// ── 6. Rate limiting ────────────────────────────────────────────────────────
//
// Per-IP cap on REST traffic. A single legitimate chat user issues maybe a
// dozen REST calls per minute; 300/min gives us 25× headroom while still
// shutting down obvious abuse. Sockets are unaffected — sustained chat
// throughput happens over the WebSocket and bypasses this gate.

const restLimiter = rateLimit({
    windowMs: 60 * 1000,
    limit: Number(process.env.REST_RATE_LIMIT || 300),
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    keyGenerator: (req) => req.user?.uid || req.ip,
    skip: (req) => req.path === '/' || req.path === '/healthz',
});
app.use('/api/', restLimiter);

// ── 7. Routes ───────────────────────────────────────────────────────────────

app.use('/api/conversations', conversationRoutes);
app.use('/api/directions', directionsRoutes);

app.get('/', (_req, res) => res.send('Where API & WebSocket Server is running.'));
app.get('/healthz', (_req, res) => res.json({ ok: true }));

// ── 8. HTTP + Socket.IO server ──────────────────────────────────────────────

const server = http.createServer(app);

// Cloud Run's load balancer caps idle connections around 60 s; we keep our
// keep-alive a touch shorter so we never close a half-open socket we'd then
// try to reuse, and a touch longer than headersTimeout to satisfy Node's
// invariant.
server.keepAliveTimeout = 65 * 1000;
server.headersTimeout = 70 * 1000;
server.requestTimeout = 0;

const io = new Server(server, {
    cors: corsOptions,
    // Aggressive ping settings keep dead mobile sockets from sitting around
    // wasting Firestore listener resources after a backgrounding kill.
    pingInterval: 25_000,
    pingTimeout: 20_000,
    // Defer to Socket.IO's perMessageDeflate default (off for binary).
    maxHttpBufferSize: 1e6, // 1 MB
});

// Initialize Socket.IO logic (auth + chat + presence + location)
require('./src/socket').initializeSockets(io);

// ── 9. Listen + graceful shutdown ───────────────────────────────────────────

const PORT = Number(process.env.PORT) || 8080;
server.listen(PORT, '0.0.0.0', () => {
    console.log(`Server listening on 0.0.0.0:${PORT}`);
});

let shuttingDown = false;
function gracefulShutdown(signal) {
    if (shuttingDown) return;
    shuttingDown = true;
    console.log(`${signal} received, draining connections...`);

    // Stop accepting new HTTP requests + tell live sockets to reconnect.
    io.disconnectSockets(true);
    server.close(() => {
        console.log('HTTP server closed cleanly.');
        process.exit(0);
    });

    // Hard cap so Cloud Run's 10s SIGKILL window doesn't catch us mid-drain.
    setTimeout(() => {
        console.warn('Shutdown timeout, forcing exit.');
        process.exit(0);
    }, 8_000).unref();
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

process.on('unhandledRejection', (err) => {
    console.error('unhandledRejection', err);
});
process.on('uncaughtException', (err) => {
    console.error('uncaughtException', err);
});
