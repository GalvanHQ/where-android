# ── server/Dockerfile ──────────────────────────────────────────────────────
#
# Used when building from inside the `server/` directory (e.g. local
# `docker build server/`). The root-level Dockerfile is the one Cloud Build
# actually runs. Keep these two in lock-step.

FROM node:20-alpine AS deps

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts && npm cache clean --force

FROM node:20-alpine AS runtime

RUN apk add --no-cache tini

ENV NODE_ENV=production \
    NODE_OPTIONS="--enable-source-maps"

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY package.json ./
COPY index.js ./
COPY src/ ./src/

EXPOSE 8080

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "index.js"]
