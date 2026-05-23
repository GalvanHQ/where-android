# Server Performance Notes

This document captures the performance + production-hardening work applied
to the Node socket server (`server/`) and the rationale behind each change.
It complements `docs/NOTIFICATIONS_SETUP.md` (which covers the Firebase /
notifications side of the deploy).

## Architecture summary

- **Express 5** REST API + **Socket.IO 4** WebSocket on Node 20 LTS.
- Deployed to **Cloud Run** (`where-chat-server-node`, `us-central1`).
- Firestore + FCM via `firebase-admin`.
- Single-doc inbox model at `users/{uid}/inbox/notifications` (FIFO 200 cap).

## Changes shipped

### 1. Token verification cache (`src/authCache.js`)

`verifyIdToken()` is the hottest function across both REST and the Socket.IO
handshake. We added a shared LRU cache (`lru-cache`, 10k entries, 1h TTL
clamped to the token's own `exp`). Any user whose token is hot now pays
the ~hundred-microsecond crypto cost only on the first request of a fresh
token instead of every request.

Hit rate scales with concurrent chat usage; a user opening one chat already
fires 4-6 REST calls plus the socket — that's a 5-6× reduction in
`verifyIdToken` calls per session.

### 2. Compression middleware

`compression()` with a 1 KB threshold. Below that, gzip overhead exceeds the
saved bytes. REST payloads (chat list, message pages, system events) are
JSON, so the wire reduction is typically 3-5×.

### 3. Helmet security headers

`helmet()` with CSP off (we don't serve HTML) and `cross-origin` resource
policy. Closes the obvious headers gap (`X-Powered-By`, missing `X-Frame-
Options`, etc.) without breaking any clients.

### 4. CORS allowlist

Origin pinned via `CORS_ORIGINS` env var (comma-separated). Defaults to `*`
so the native Android client works out of the box, but production web
clients should pin their origin explicitly.

### 5. Per-IP rate limiting

`express-rate-limit` on `/api/*` at 300 req/min/uid (or per IP for
unauthenticated callers). 25× over a typical user's natural throughput.
Sockets are unaffected — chat throughput goes over the WebSocket and
bypasses the gate.

### 6. Structured request logs

One JSON line per non-health request: `reqId`, `method`, `path`, `status`,
`latencyMs`, `uid`. Plays nicely with Cloud Logging's structured filters
and gives us a real latency baseline to track regressions against.

### 7. Graceful shutdown

`SIGTERM` / `SIGINT` triggers `io.disconnectSockets(true)` + `server.close()`
with an 8-second hard cap. Cloud Run rolling deploys now finish in-flight
chats instead of dropping them mid-message.

### 8. HTTP keep-alive tuning

Cloud Run's LB caps idle connections at ~60 s; we set `keepAliveTimeout`
to 65 s and `headersTimeout` to 70 s so we never close a half-open socket
the LB still thinks is reusable.

### 9. Batched Firestore reads on FCM fan-out (`src/socket.js`)

Group chats fan out to N recipients. Previously each recipient's user doc
was fetched in its own `db.collection('users').doc(id).get()` (sequential
inside `Promise.all`, but still N round trips). Now: one
`db.getAll(...refs)` call.

For a 10-person group: 1 round trip instead of 10. Cuts FCM dispatch
latency by ~80% on group chats.

### 10. Batched FCM dispatch via `sendEach`

Replaced the per-recipient `messaging.send()` loop with
`messaging.sendEach([...])`. The Admin SDK delivers the whole batch over
one HTTP/2 connection and returns per-message responses. A slow recipient
no longer serializes the rest.

### 11. Parallel inbox transactions

Inbox writes are still transactional (two simultaneous messages to the
same user must not clobber each other), but the per-recipient transactions
now run in parallel with `Promise.all` instead of awaited inside the FCM
loop.

### 12. Socket.IO ping settings

`pingInterval=25s`, `pingTimeout=20s`. Aggressive enough to detect dead
mobile sockets quickly (so Firestore listeners aren't pinned by zombie
connections) without burning radio on healthy ones.

### 13. Cloud Run config (`cloudbuild.yaml`)

| Setting          | Old   | New   | Why                                               |
| ---------------- | ----- | ----- | ------------------------------------------------- |
| memory           | 256Mi | 512Mi | RSS hits ~180MB under modest load.                |
| min-instances    | 0     | 1     | Eliminates cold start on chat / location traffic. |
| max-instances    | 5     | 10    | Headroom for fan-out spikes.                      |
| cpu-boost        | -     | on    | Faster start-up on scale-out.                     |
| session-affinity | -     | on    | Stickiness for Socket.IO long-poll fallback.      |

### 14. Multi-stage Dockerfile + tini

Dev dependencies stripped from the runtime layer (`npm ci --omit=dev`),
which trims the image. `tini` as PID 1 forwards `SIGTERM` correctly so
graceful shutdown actually fires on Cloud Run rolling deploys.

## Operator deploy

```bash
# From repo root:
gcloud builds submit --config=cloudbuild.yaml .
```

Verify the new instance came up cleanly:

```bash
gcloud run services describe where-chat-server-node \
  --region=us-central1 --format="value(status.latestReadyRevisionName)"
```

Tail logs to confirm the new structured log format:

```bash
gcloud run logs read --service=where-chat-server-node \
  --region=us-central1 --limit=20
```

## Tunables (env vars)

| Var               | Default | Notes                                     |
| ----------------- | ------- | ----------------------------------------- |
| `PORT`            | `8080`  | Cloud Run sets this; don't override.      |
| `CORS_ORIGINS`    | `*`     | Comma-separated origin allowlist.         |
| `REST_RATE_LIMIT` | `300`   | Requests / minute per uid (or IP).        |
| `NODE_ENV`        | -       | Set to `production` in cloudbuild.yaml.   |

## Known follow-ups (not in this PR)

- **Reaction transactions still re-read the whole `recentMessages` array.**
  Could be reduced to a sub-field write under `recentMessages.<i>.reactions`
  with FieldPath, but that requires reordering reads which complicates
  idempotency. Left as-is.
- **Location relay is in-memory only** (Socket.IO room broadcast). If we
  ever scale beyond one Cloud Run instance, we'll need a Socket.IO adapter
  (Redis or Pub/Sub) so location frames cross instance boundaries.
- **`/api/directions`** has no caching layer. A short-TTL LRU keyed by
  rounded origin/destination + mode would cut the Routes API bill noticeably
  for hot meetup destinations.
