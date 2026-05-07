# Stage 1: Build Dependencies
FROM node:21-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY index.js ./
COPY src/ ./src/


# Stage 2: Production Image (minimal)
FROM node:21-alpine

WORKDIR /app

ENV NODE_ENV=production

COPY package*.json ./
RUN npm ci --omit=dev

COPY --from=builder /app/index.js ./
COPY --from=builder /app/src ./src

RUN addgroup -S nodejs && adduser -S nodejs -G nodejs
USER nodejs

EXPOSE 8080

CMD ["node", "index.js"]