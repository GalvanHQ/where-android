FROM node:21-alpine

WORKDIR /app

COPY server/package.json server/package-lock.json ./
RUN npm install --production

COPY server/index.js ./
COPY server/src/ ./src/

EXPOSE 8080

CMD ["node", "index.js"]