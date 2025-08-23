// MongoDB initialization script for Docker setup
db = db.getSiblingDB('skyflow');

// Create collections with proper indexes
db.createCollection('user_sessions');
db.createCollection('audit_logs');
db.createCollection('analytics');
db.createCollection('notifications');

// Create indexes for better performance
db.user_sessions.createIndex({ sessionId: 1 }, { unique: true });
db.user_sessions.createIndex({ userId: 1 });
db.user_sessions.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 });

db.audit_logs.createIndex({ timestamp: -1 });
db.audit_logs.createIndex({ userId: 1 });
db.audit_logs.createIndex({ action: 1 });

db.analytics.createIndex({ timestamp: -1 });
db.analytics.createIndex({ event: 1 });
db.analytics.createIndex({ userId: 1 });

db.notifications.createIndex({ userId: 1 });
db.notifications.createIndex({ read: 1 });
db.notifications.createIndex({ createdAt: -1 });

// Create admin user for monitoring
db.createUser({
  user: 'admin',
  pwd: 'password',
  roles: [
    { role: 'readWrite', db: 'skyflow' },
    { role: 'dbAdmin', db: 'skyflow' },
  ],
});

print('MongoDB initialization completed successfully!');
