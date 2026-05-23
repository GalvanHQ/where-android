# ── Where chat / location server ───────────────────────────────────────────
#
# Multi-stage build:
#   1. `deps` installs production-only npm packages with a clean cache so the
#      builder layer stays small and reproducible.
#   2. The runtime image only carries node_modules + source — no npm, no toolchain.
#
# Node 20 LTS — Cloud Run supports it, and Firebase Admin 13 / Socket.IO 4
# are both happy on it.

FROM node:20-alpine AS deps

WORKDIR /app

# Copy lockfile so `npm ci` is fully reproducible. --omit=dev keeps prod-only.
COPY server/package.json server/package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts && npm cache clean --force

FROM node:20-alpine AS runtime

# tini is a tiny init that forwards SIGTERM correctly on Cloud Run rolling
# deploys; without it Node sees PID 1 and graceful shutdown becomes unreliable.
RUN apk add --no-cache tini

ENV NODE_ENV=production \
    NODE_OPTIONS="--enable-source-maps"

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY server/package.json ./
COPY server/index.js ./
COPY server/src/ ./src/

EXPOSE 8080

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "index.js"]
