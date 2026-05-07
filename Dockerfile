FROM node:21-alpine

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm install --production

COPY index.js ./
COPY src/ ./src/

EXPOSE 8080

CMD ["node", "index.js"]