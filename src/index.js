const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
const helmet = require('helmet');
const compression = require('compression');
const morgan = require('morgan');
const rateLimit = require('express-rate-limit');
const winston = require('winston');

const {
  PORT, RATE_LIMIT, CORS, LOGGING,
} = require('./config/serverConfig');
const ApiRoutes = require('./routes/index');
const db = require('./models/index');
const RabbitMQService = require('./services/rabbitmq-service');
const RedisService = require('./services/redis-service');
const MongoService = require('./services/mongo-service');
const EmailService = require('./services/email-service');

// Configure logging
const logger = winston.createLogger({
  level: LOGGING.level,
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.errors({ stack: true }),
    winston.format.json(),
  ),
  defaultMeta: { service: 'skyflow-api' },
  transports: [
    new winston.transports.File({ filename: LOGGING.file }),
    new winston.transports.Console({
      format: winston.format.simple(),
    }),
  ],
});

const setupAndStartServer = async () => {
  // Initialize services
  const rabbitmq = new RabbitMQService();
  const redis = new RedisService();
  const mongo = new MongoService();
  const emailService = new EmailService();

  try {
    logger.info('Initializing services...');

    // Connect to all services
    await Promise.all([
      rabbitmq.connect(),
      redis.connect(),
      mongo.connect(),
      emailService.setupConsumer(),
    ]);

    logger.info('All services initialized successfully');
  } catch (error) {
    logger.error('Failed to initialize services:', error);
    process.exit(1);
  }

  // Create express app
  const app = express();

  // Security middleware
  app.use(helmet());

  // CORS middleware
  app.use(cors(CORS));

  // Compression middleware
  app.use(compression());

  // Rate limiting
  const limiter = rateLimit(RATE_LIMIT);
  app.use('/api/', limiter);

  // Logging middleware
  app.use(morgan('combined', { stream: { write: (message) => logger.info(message.trim()) } }));

  // Body parsing middleware
  app.use(bodyParser.json({ limit: '10mb' }));
  app.use(bodyParser.urlencoded({ extended: true, limit: '10mb' }));

  // Request logging middleware
  app.use((req, res, next) => {
    logger.info(`${req.method} ${req.path}`, {
      ip: req.ip,
      userAgent: req.get('User-Agent'),
      timestamp: new Date().toISOString(),
    });
    next();
  });

  // API routes
  app.use('/api', ApiRoutes);

  // Health check endpoint
  app.get('/health', async (req, res) => {
    try {
      const healthStatus = {
        status: 'OK',
        timestamp: new Date().toISOString(),
        uptime: process.uptime(),
        memory: process.memoryUsage(),
        services: {
          database: 'unknown',
          redis: 'unknown',
          rabbitmq: 'unknown',
          mongodb: 'unknown',
        },
      };

      // Check database connection
      try {
        await db.sequelize.authenticate();
        healthStatus.services.database = 'connected';
      } catch (error) {
        healthStatus.services.database = 'disconnected';
        logger.error('Database health check failed:', error);
      }

      // Check Redis connection
      try {
        const redisStatus = await redis.isConnected();
        healthStatus.services.redis = redisStatus ? 'connected' : 'disconnected';
      } catch (error) {
        healthStatus.services.redis = 'disconnected';
        logger.error('Redis health check failed:', error);
      }

      // Check RabbitMQ connection
      try {
        healthStatus.services.rabbitmq = rabbitmq.isConnected ? 'connected' : 'disconnected';
      } catch (error) {
        healthStatus.services.rabbitmq = 'disconnected';
        logger.error('RabbitMQ health check failed:', error);
      }

      // Check MongoDB connection
      try {
        const mongoStatus = await mongo.isConnected();
        healthStatus.services.mongodb = mongoStatus ? 'connected' : 'disconnected';
      } catch (error) {
        healthStatus.services.mongodb = 'disconnected';
        logger.error('MongoDB health check failed:', error);
      }

      // Determine overall status
      const allServicesConnected = Object.values(healthStatus.services).every((status) => status === 'connected');
      const statusCode = allServicesConnected ? 200 : 503;
      healthStatus.status = allServicesConnected ? 'OK' : 'DEGRADED';

      res.status(statusCode).json(healthStatus);
    } catch (error) {
      logger.error('Health check failed:', error);
      res.status(500).json({
        status: 'ERROR',
        timestamp: new Date().toISOString(),
        error: error.message,
      });
    }
  });

  // Metrics endpoint
  app.get('/metrics', (req, res) => {
    const metrics = {
      uptime: process.uptime(),
      memory: process.memoryUsage(),
      cpu: process.cpuUsage(),
      timestamp: new Date().toISOString(),
    };
    res.json(metrics);
  });

  // 404 handler
  app.use('*', (req, res) => {
    res.status(404).json({
      success: false,
      message: 'Route not found',
      path: req.originalUrl,
    });
  });

  // Global error handler
  app.use((error, req, res, next) => {
    logger.error('Unhandled error:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error',
      error: process.env.NODE_ENV === 'development' ? error.message : 'Something went wrong',
    });
  });

  // Start server
  const server = app.listen(PORT, async () => {
    logger.info(`SkyFlow server started on port ${PORT}`);
    logger.info(`Environment: ${process.env.NODE_ENV}`);

    // Sync database if needed
    if (process.env.SYNC_DB === 'true') {
      try {
        await db.sequelize.sync({ alter: true });
        logger.info('Database synchronized');
      } catch (error) {
        logger.error('Database sync failed:', error);
      }
    }

    // Start consuming notifications
    try {
      await rabbitmq.consume((message) => {
        logger.info('Received notification:', message);
      });
    } catch (error) {
      logger.error('Failed to start notification consumer:', error);
    }
  });

  // Graceful shutdown
  const gracefulShutdown = async (signal) => {
    logger.info(`Received ${signal}. Starting graceful shutdown...`);

    server.close(async () => {
      try {
        await Promise.all([
          rabbitmq.close(),
          redis.disconnect(),
          mongo.disconnect(),
          db.sequelize.close(),
        ]);
        logger.info('All connections closed. Shutdown complete.');
        process.exit(0);
      } catch (error) {
        logger.error('Error during shutdown:', error);
        process.exit(1);
      }
    });
  };

  process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
  process.on('SIGINT', () => gracefulShutdown('SIGINT'));
};

setupAndStartServer().catch((error) => {
  logger.error('Failed to start server:', error);
  process.exit(1);
});
