FROM node:20-alpine AS builder

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --production --ignore-scripts && npm cache clean --force

FROM node:20-alpine

WORKDIR /app
COPY --from=builder /app/node_modules ./node_modules
COPY package.json ./
COPY index.js ./
COPY src/ ./src/

EXPOSE 8080

CMD ["node", "index.js"]