// ── /api/directions ──────────────────────────────────────────────────────────
//
// Server-side proxy for Google's Routes API. Keeps the Cloud API key off
// every device (the Android app shipped a restricted key that, for native
// REST calls, requires extra X-Android-* header voodoo to authenticate
// — proxying through the server sidesteps that entire surface).
//
// Endpoint shape:
//   POST /api/directions
//   body: { originLat, originLng, destLat, destLng, mode? }
//   200: { distanceMeters, durationSeconds, encodedPolyline }
//   4xx/5xx: { error: "<readable message>" }
//
// Auth: piggybacks on the global Firebase ID-token middleware in
// index.js — only authenticated app users can call this proxy.

const express = require('express');

const router = express.Router();

const ROUTES_API_URL =
    'https://routes.googleapis.com/directions/v2:computeRoutes';

// Field mask scopes the response to only what the client renders.
// Cost + latency both scale with the mask, so keep it tight.
const FIELD_MASK =
    'routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline';

const VALID_MODES = new Set([
    'DRIVE',
    'WALK',
    'BICYCLE',
    'TWO_WHEELER',
    'TRANSIT',
]);

// ── Auth gate ────────────────────────────────────────────────────────────────
router.use((req, res, next) => {
    if (!req.user) {
        return res.status(401).json({ error: 'Unauthorized' });
    }
    next();
});

// ── POST /api/directions ─────────────────────────────────────────────────────
router.post('/', async (req, res) => {
    const { originLat, originLng, destLat, destLng, mode } = req.body || {};

    // Coerce + validate. Reject early so we don't bill Routes for noise.
    const oLat = Number(originLat);
    const oLng = Number(originLng);
    const dLat = Number(destLat);
    const dLng = Number(destLng);
    if (
        !Number.isFinite(oLat) ||
        !Number.isFinite(oLng) ||
        !Number.isFinite(dLat) ||
        !Number.isFinite(dLng)
    ) {
        return res
            .status(400)
            .json({ error: 'originLat/originLng/destLat/destLng must be numbers' });
    }
    const travelMode =
        typeof mode === 'string' && VALID_MODES.has(mode.toUpperCase())
            ? mode.toUpperCase()
            : 'DRIVE';

    const apiKey = process.env.GOOGLE_ROUTES_API_KEY;
    if (!apiKey) {
        // Fail loud in logs but generic to the client.
        console.error('directions: GOOGLE_ROUTES_API_KEY not configured');
        return res
            .status(500)
            .json({ error: 'Directions service is not configured.' });
    }

    const payload = {
        origin: { location: { latLng: { latitude: oLat, longitude: oLng } } },
        destination: { location: { latLng: { latitude: dLat, longitude: dLng } } },
        travelMode,
        polylineEncoding: 'ENCODED_POLYLINE',
        // TRAFFIC_AWARE is only valid for DRIVE / TWO_WHEELER. The Routes
        // API rejects the field on WALK / BICYCLE / TRANSIT, so omit it
        // for those.
        ...(travelMode === 'DRIVE' || travelMode === 'TWO_WHEELER'
            ? { routingPreference: 'TRAFFIC_AWARE' }
            : {}),
    };

    try {
        const upstream = await fetch(ROUTES_API_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Goog-Api-Key': apiKey,
                'X-Goog-FieldMask': FIELD_MASK,
            },
            body: JSON.stringify(payload),
        });

        const text = await upstream.text();
        if (!upstream.ok) {
            // Bubble the upstream message up so the caller can see what's
            // wrong (API not enabled, billing, etc.) but never leak our key.
            console.warn(
                `directions: upstream ${upstream.status} ${upstream.statusText}: ${text}`
            );
            let message = 'Could not load directions.';
            try {
                const parsed = JSON.parse(text);
                message = parsed?.error?.message || message;
            } catch (_) {
                /* keep generic */
            }
            return res.status(upstream.status).json({ error: message });
        }

        let parsed;
        try {
            parsed = JSON.parse(text);
        } catch (e) {
            console.error('directions: invalid upstream JSON', e);
            return res
                .status(502)
                .json({ error: 'Bad response from directions provider.' });
        }

        const route = (parsed.routes || [])[0];
        const encodedPolyline = route?.polyline?.encodedPolyline;
        if (!route || !encodedPolyline) {
            return res.status(404).json({ error: 'No route returned.' });
        }

        // duration comes back as `"<seconds>s"` (Google Duration proto).
        const durationSeconds = Number(
            (route.duration || '0s').toString().replace(/s$/, '')
        );

        res.json({
            distanceMeters: Number(route.distanceMeters || 0),
            durationSeconds: Number.isFinite(durationSeconds) ? durationSeconds : 0,
            encodedPolyline,
        });
    } catch (err) {
        console.error('directions: request failed', err);
        res.status(500).json({ error: 'Could not load directions.' });
    }
});

module.exports = router;
