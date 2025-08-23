const mongoose = require('mongoose');
const { MONGODB } = require('../config/serverConfig');

class MongoService {
  constructor() {
    this.isConnected = false;
    this.connection = null;
  }

  async connect() {
    try {
      if (this.isConnected) {
        return;
      }

      this.connection = await mongoose.connect(MONGODB.url, MONGODB.options);
      this.isConnected = true;

      console.log('MongoDB connected successfully');

      // Handle connection events
      mongoose.connection.on('error', (error) => {
        console.error('MongoDB connection error:', error);
        this.isConnected = false;
      });

      mongoose.connection.on('disconnected', () => {
        console.log('MongoDB disconnected');
        this.isConnected = false;
      });

      mongoose.connection.on('reconnected', () => {
        console.log('MongoDB reconnected');
        this.isConnected = true;
      });
    } catch (error) {
      console.error('MongoDB connection failed:', error);
      this.isConnected = false;
      throw error;
    }
  }

  async disconnect() {
    try {
      if (this.connection) {
        await mongoose.disconnect();
        this.isConnected = false;
        console.log('MongoDB disconnected successfully');
      }
    } catch (error) {
      console.error('MongoDB disconnect error:', error);
      throw error;
    }
  }

  async isConnected() {
    return this.isConnected && mongoose.connection.readyState === 1;
  }

  // Generic CRUD operations
  async create(collection, data) {
    try {
      const model = this.getModel(collection);
      const document = new model(data);
      return await document.save();
    } catch (error) {
      console.error('MongoDB create error:', error);
      throw error;
    }
  }

  async find(collection, query = {}, options = {}) {
    try {
      const model = this.getModel(collection);
      return await model.find(query, null, options);
    } catch (error) {
      console.error('MongoDB find error:', error);
      throw error;
    }
  }

  async findOne(collection, query = {}) {
    try {
      const model = this.getModel(collection);
      return await model.findOne(query);
    } catch (error) {
      console.error('MongoDB findOne error:', error);
      throw error;
    }
  }

  async findById(collection, id) {
    try {
      const model = this.getModel(collection);
      return await model.findById(id);
    } catch (error) {
      console.error('MongoDB findById error:', error);
      throw error;
    }
  }

  async update(collection, query, data) {
    try {
      const model = this.getModel(collection);
      return await model.updateMany(query, data, { new: true });
    } catch (error) {
      console.error('MongoDB update error:', error);
      throw error;
    }
  }

  async updateOne(collection, query, data) {
    try {
      const model = this.getModel(collection);
      return await model.findOneAndUpdate(query, data, { new: true });
    } catch (error) {
      console.error('MongoDB updateOne error:', error);
      throw error;
    }
  }

  async delete(collection, query) {
    try {
      const model = this.getModel(collection);
      return await model.deleteMany(query);
    } catch (error) {
      console.error('MongoDB delete error:', error);
      throw error;
    }
  }

  async deleteOne(collection, query) {
    try {
      const model = this.getModel(collection);
      return await model.findOneAndDelete(query);
    } catch (error) {
      console.error('MongoDB deleteOne error:', error);
      throw error;
    }
  }

  // Get model by collection name
  getModel(collection) {
    // Define schemas for different collections
    const schemas = {
      user_sessions: new mongoose.Schema({
        userId: { type: String, required: true },
        sessionId: { type: String, required: true },
        data: { type: mongoose.Schema.Types.Mixed },
        createdAt: { type: Date, default: Date.now },
        expiresAt: { type: Date, required: true },
      }, { timestamps: true }),

      audit_logs: new mongoose.Schema({
        action: { type: String, required: true },
        userId: { type: String },
        resource: { type: String },
        details: { type: mongoose.Schema.Types.Mixed },
        ipAddress: { type: String },
        userAgent: { type: String },
        timestamp: { type: Date, default: Date.now },
      }, { timestamps: true }),

      analytics: new mongoose.Schema({
        event: { type: String, required: true },
        userId: { type: String },
        data: { type: mongoose.Schema.Types.Mixed },
        timestamp: { type: Date, default: Date.now },
      }, { timestamps: true }),

      notifications: new mongoose.Schema({
        userId: { type: String, required: true },
        type: { type: String, required: true },
        title: { type: String, required: true },
        message: { type: String, required: true },
        read: { type: Boolean, default: false },
        data: { type: mongoose.Schema.Types.Mixed },
        createdAt: { type: Date, default: Date.now },
      }, { timestamps: true }),
    };

    const schema = schemas[collection];
    if (!schema) {
      throw new Error(`Schema not found for collection: ${collection}`);
    }

    // Create model if it doesn't exist
    return mongoose.models[collection] || mongoose.model(collection, schema);
  }

  // Specific methods for common operations
  async createUserSession(userId, sessionData, expiresIn = 24 * 60 * 60 * 1000) {
    const session = {
      userId,
      sessionId: require('uuid').v4(),
      data: sessionData,
      expiresAt: new Date(Date.now() + expiresIn),
    };
    return await this.create('user_sessions', session);
  }

  async getUserSession(sessionId) {
    return await this.findOne('user_sessions', {
      sessionId,
      expiresAt: { $gt: new Date() },
    });
  }

  async createAuditLog(action, userId, resource, details, ipAddress, userAgent) {
    return await this.create('audit_logs', {
      action,
      userId,
      resource,
      details,
      ipAddress,
      userAgent,
    });
  }

  async createAnalyticsEvent(event, userId, data) {
    return await this.create('analytics', {
      event,
      userId,
      data,
    });
  }

  async createNotification(userId, type, title, message, data = {}) {
    return await this.create('notifications', {
      userId,
      type,
      title,
      message,
      data,
    });
  }

  async getUnreadNotifications(userId) {
    return await this.find('notifications', {
      userId,
      read: false,
    }, { sort: { createdAt: -1 } });
  }

  async markNotificationAsRead(notificationId) {
    return await this.updateOne(
      'notifications',
      { _id: notificationId },
      { read: true },
    );
  }
}

module.exports = MongoService;
