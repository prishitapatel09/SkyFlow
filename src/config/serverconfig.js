const dotenv = require('dotenv');

dotenv.config();

module.exports = {
  PORT: process.env.PORT || 3000,
  NODE_ENV: process.env.NODE_ENV || 'development',

  // Database Configuration
  DB: {
    host: process.env.DB_HOST || 'localhost',
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || 'password',
    database: process.env.DB_NAME || 'skyflow_dev',
    dialect: process.env.DB_DIALECT || 'postgres',
    port: process.env.DB_PORT || 5432,
  },

  // MongoDB Configuration
  MONGODB: {
    url: process.env.MONGODB_URL || 'mongodb://localhost:27017/skyflow',
    options: {
      useNewUrlParser: true,
      useUnifiedTopology: true,
      maxPoolSize: 10,
      serverSelectionTimeoutMS: 5000,
      socketTimeoutMS: 45000,
    },
  },

  // Redis Configuration
  REDIS: {
    url: process.env.REDIS_URL || 'redis://localhost:6379',
    options: {
      retryDelayOnFailover: 100,
      enableReadyCheck: false,
      maxRetriesPerRequest: null,
    },
  },

  // RabbitMQ Configuration
  RABBITMQ: {
    url: process.env.RABBITMQ_URL || 'amqp://localhost',
    options: {
      heartbeat: 60,
      connection_timeout: 60000,
    },
  },

  // Email Configuration
  EMAIL: {
    user: process.env.EMAIL_USER || '',
    password: process.env.EMAIL_PASSWORD || '',
    service: process.env.EMAIL_SERVICE || 'gmail',
    from: process.env.EMAIL_FROM || 'noreply@skyflow.com',
  },

  // JWT Configuration
  JWT: {
    secret: process.env.JWT_SECRET || 'your-secret-key',
    expiresIn: process.env.JWT_EXPIRES_IN || '24h',
  },

  // Stripe Configuration
  STRIPE: {
    secretKey: process.env.STRIPE_SECRET_KEY || '',
    publishableKey: process.env.STRIPE_PUBLISHABLE_KEY || '',
    webhookSecret: process.env.STRIPE_WEBHOOK_SECRET || '',
    currency: process.env.STRIPE_CURRENCY || 'usd',
    paymentMethods: ['card', 'upi', 'netbanking'],
    supportedCountries: ['US', 'IN', 'CA', 'GB', 'AU'],
  },

  // Rate Limiting
  RATE_LIMIT: {
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 100, // limit each IP to 100 requests per windowMs
    message: 'Too many requests from this IP, please try again later.',
  },

  // CORS Configuration
  CORS: {
    origin: process.env.CORS_ORIGIN || '*',
    credentials: true,
  },

  // Logging
  LOGGING: {
    level: process.env.LOG_LEVEL || 'info',
    file: process.env.LOG_FILE || 'logs/app.log',
  },
};
