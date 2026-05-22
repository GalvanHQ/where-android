require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const { auth } = require('./src/firebase');
const conversationRoutes = require('./src/routes/conversations');
const directionsRoutes = require('./src/routes/directions');

const app = express();
const server = http.createServer(app);

// Configure Socket.IO
const io = new Server(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST", "PATCH"]
    }
});

app.use(cors());
app.use(express.json());

// Global Auth Middleware for REST API
app.use(async (req, res, next) => {
    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
        const token = authHeader.split('Bearer ')[1];
        try {
            const decodedToken = await auth.verifyIdToken(token);
            req.user = decodedToken;
        } catch (err) {
            console.error('REST auth failed', err);
        }
    }
    next();
});

// Routes
app.use('/api/conversations', conversationRoutes);
app.use('/api/directions', directionsRoutes);

// Health check
app.get('/', (req, res) => {
    res.send('Where App API & WebSocket Server is running.');
});

// Initialize Socket.IO logic
require('./src/socket').initializeSockets(io);

const PORT = process.env.PORT || 8080;
server.listen(PORT, '0.0.0.0', () => {
    console.log(`Server listening on 0.0.0.0:${PORT}`);
});
