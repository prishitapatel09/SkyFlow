const redis = require('redis');
const { REDIS } = require('../config/serverConfig');

class RedisService {
  constructor() {
    this.client = redis.createClient({
      url: REDIS.url,
    });
    this.isConnected = false;
    this.setupEventHandlers();
  }

  setupEventHandlers() {
    this.client.on('error', (err) => {
      console.log('Redis Client Error', err);
      this.isConnected = false;
    });

    this.client.on('ready', () => {
      console.log('Redis client connected');
      this.isConnected = true;
    });
  }

  async connect() {
    try {
      await this.client.connect();
      this.isConnected = true;
    } catch (error) {
      console.log('Redis connection failed', error);
      this.isConnected = false;
      throw error;
    }
  }

  async isConnected() {
    return this.isConnected;
  }

  async set(key, value, expiry = 3600) {
    try {
      await this.client.set(key, JSON.stringify(value), {
        EX: expiry,
      });
    } catch (error) {
      console.log('Something went wrong in redis set');
      throw error;
    }
  }

  async get(key) {
    try {
      const data = await this.client.get(key);
      return data ? JSON.parse(data) : null;
    } catch (error) {
      console.log('Something went wrong in redis get');
      throw error;
    }
  }

  async delete(key) {
    try {
      await this.client.del(key);
    } catch (error) {
      console.log('Something went wrong in redis delete');
      throw error;
    }
  }

  async disconnect() {
    try {
      await this.client.disconnect();
      this.isConnected = false;
    } catch (error) {
      console.log('Something went wrong in redis disconnect');
      throw error;
    }
  }
}

module.exports = RedisService;
