const admin = require('firebase-admin');
const path = require('path');

// Initialize Firebase Admin SDK with service account key for local dev.
// In production (GCP), use admin.initializeApp() without args.
if (!admin.apps.length) {
    const serviceAccountPath = path.resolve(__dirname, '..', 'serviceAccountKey.json');
    
    try {
        const serviceAccount = require(serviceAccountPath);
        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount)
        });
        console.log('Firebase Admin initialized with service account key');
    } catch (err) {
        // Fallback: try default credentials (works on GCP / with GOOGLE_APPLICATION_CREDENTIALS)
        console.warn('serviceAccountKey.json not found, trying default credentials...');
        admin.initializeApp();
    }
}

const db = admin.firestore();
const messaging = admin.messaging();
const auth = admin.auth();

module.exports = {
    admin,
    db,
    messaging,
    auth
};
