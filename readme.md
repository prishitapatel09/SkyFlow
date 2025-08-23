# SkyFlow - Airline Backend System

A comprehensive microservices-based airline backend system built with Node.js, featuring REST APIs for interservice communication, RabbitMQ for asynchronous reminder notifications, and a modern tech stack for scalability and performance.

## 🚀 Technologies & Architecture

### Core Technologies
- **Node.js 18+** - Runtime environment
- **Express.js** - Web framework
- **PostgreSQL** - Primary relational database
- **MongoDB** - Document database for sessions, logs, and analytics
- **Redis** - Caching and session storage
- **RabbitMQ** - Message queuing for notifications
- **Docker** - Containerization
- **NGINX** - Reverse proxy with rate limiting and SSL
- **GitHub Actions** - CI/CD pipeline
- **Stripe** - Payment processing

### Additional Features
- **JWT Authentication** - Secure API access
- **Rate Limiting** - API protection
- **CORS Support** - Cross-origin requests
- **Compression** - Response optimization
- **Helmet** - Security headers
- **Winston Logging** - Structured logging
- **Prometheus & Grafana** - Monitoring and observability
- **SSL/TLS** - Secure communication
- **Payment Processing** - Stripe integration for flight bookings

## 🏗️ Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Frontend      │    │   Mobile App    │    │   Third Party   │
│   (React/Vue)   │    │   (React Native)│    │   APIs          │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌─────────────▼─────────────┐
                    │        NGINX              │
                    │   (Load Balancer/SSL)     │
                    └─────────────┬─────────────┘
                                  │
                    ┌─────────────▼─────────────┐
                    │    SkyFlow API            │
                    │   (Node.js/Express)       │
                    └─────────────┬─────────────┘
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
┌───────▼────────┐    ┌───────────▼──────────┐    ┌────────▼────────┐
│   PostgreSQL   │    │      MongoDB         │    │      Redis      │
│   (Flights,    │    │   (Sessions, Logs,   │    │   (Caching,     │
│    Cities,     │    │    Analytics)        │    │    Sessions)    │
│    Airports)   │    │                      │    │                 │
└────────────────┘    └──────────────────────┘    └─────────────────┘
        │                         │                         │
        └─────────────────────────┼─────────────────────────┘
                                  │
                    ┌─────────────▼─────────────┐
                    │      RabbitMQ             │
                    │   (Message Queue)         │
                    └───────────────────────────┘
                                  │
                    ┌─────────────▼─────────────┐
                    │      Stripe               │
                    │   (Payment Processing)    │
                    └───────────────────────────┘
```

## 📋 Prerequisites

- Node.js 18+
- Docker & Docker Compose
- PostgreSQL 15+
- MongoDB 6.0+
- Redis 7+
- RabbitMQ 3.9+
- Stripe Account (for payment processing)

## 🛠️ Installation & Setup

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/skyflow.git
cd skyflow
```

### 2. Environment Configuration
Create a `.env` file in the root directory:

```env
# Server Configuration
PORT=3000
NODE_ENV=development

# Database Configuration
DB_HOST=localhost
DB_USER=postgres
DB_PASSWORD=password
DB_NAME=skyflow_dev
DB_DIALECT=postgres
DB_PORT=5432

# MongoDB Configuration
MONGODB_URL=mongodb://localhost:27017/skyflow

# Redis Configuration
REDIS_URL=redis://localhost:6379

# RabbitMQ Configuration
RABBITMQ_URL=amqp://localhost

# Email Configuration (for Gmail)
EMAIL_USER=your-email@gmail.com
EMAIL_PASSWORD=your-app-password
EMAIL_SERVICE=gmail
EMAIL_FROM=noreply@skyflow.com

# JWT Configuration
JWT_SECRET=your-super-secret-jwt-key-change-in-production
JWT_EXPIRES_IN=24h

# Stripe Configuration
STRIPE_SECRET_KEY=sk_test_your_stripe_secret_key
STRIPE_PUBLISHABLE_KEY=pk_test_your_stripe_publishable_key
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret
STRIPE_CURRENCY=usd

# Rate Limiting
RATE_LIMIT_WINDOW_MS=900000
RATE_LIMIT_MAX_REQUESTS=100

# CORS Configuration
CORS_ORIGIN=*

# Logging
LOG_LEVEL=info
LOG_FILE=logs/app.log

# Database Sync (set to true to auto-sync database schema)
SYNC_DB=false
```

### 3. Quick Start with Docker
```bash
# Build and start all services
docker-compose up --build

# Or run in background
docker-compose up -d --build
```

### 4. Local Development Setup
```bash
# Install dependencies
npm install

# Create database
createdb skyflow_dev

# Run migrations
npm run migrate

# Run seeders
npm run seed

# Start development server
npm run dev
```

## 🚀 API Endpoints

### Authentication
- `POST /api/v1/auth/login` - User login
- `POST /api/v1/auth/register` - User registration
- `POST /api/v1/auth/refresh` - Refresh token
- `POST /api/v1/auth/logout` - User logout

### Flights
- `GET /api/v1/flights` - Get all flights
- `GET /api/v1/flights/:id` - Get flight by ID
- `POST /api/v1/flights` - Create new flight
- `PUT /api/v1/flights/:id` - Update flight
- `DELETE /api/v1/flights/:id` - Delete flight
- `GET /api/v1/flights/search` - Search flights

### Cities
- `GET /api/v1/cities` - Get all cities
- `GET /api/v1/cities/:id` - Get city by ID
- `POST /api/v1/cities` - Create new city
- `PUT /api/v1/cities/:id` - Update city
- `DELETE /api/v1/cities/:id` - Delete city

### Airports
- `GET /api/v1/airports` - Get all airports
- `GET /api/v1/airports/:id` - Get airport by ID
- `POST /api/v1/airports` - Create new airport
- `PUT /api/v1/airports/:id` - Update airport
- `DELETE /api/v1/airports/:id` - Delete airport

### Payments
- `POST /api/v1/payments/intent` - Create payment intent
- `POST /api/v1/payments/:paymentIntentId/confirm` - Confirm payment
- `GET /api/v1/payments/:paymentIntentId` - Get payment details
- `POST /api/v1/payments/:paymentIntentId/refund` - Refund payment
- `POST /api/v1/payments/:paymentIntentId/cancel` - Cancel payment
- `GET /api/v1/payments/methods` - Get supported payment methods

### Customers
- `POST /api/v1/customers` - Create customer
- `GET /api/v1/customers/:customerId` - Get customer details
- `GET /api/v1/customers/:customerId/payments` - Get customer payment history

### System
- `GET /health` - Health check
- `GET /metrics` - Application metrics
- `GET /api/v1/notifications` - Get user notifications
- `POST /webhooks/stripe` - Stripe webhook endpoint

## 💳 Payment Integration

### Stripe Payment Flow

1. **Create Payment Intent:**
```bash
POST /api/v1/payments/intent
{
  "amount": 150.00,
  "currency": "usd",
  "customerId": "cus_123",
  "flightId": "flight_456",
  "bookingId": "booking_789",
  "description": "Flight booking payment"
}
```

2. **Confirm Payment:**
```bash
POST /api/v1/payments/pi_123/confirm
{
  "paymentMethodId": "pm_456"
}
```

3. **Get Payment Details:**
```bash
GET /api/v1/payments/pi_123
```

4. **Refund Payment:**
```bash
POST /api/v1/payments/pi_123/refund
{
  "amount": 75.00,
  "reason": "requested_by_customer"
}
```

### Supported Payment Methods
- Credit/Debit Cards
- UPI (India)
- Net Banking
- Digital Wallets

### Supported Currencies
- USD (US Dollar)
- INR (Indian Rupee)
- EUR (Euro)
- GBP (British Pound)

## 🧪 Testing

```bash
# Run all tests
npm test

# Run specific test suites
npm run test:unit
npm run test:integration
npm run test:redis
npm run test:rabbitmq
npm run test:postgres
npm run test:mongo
npm run test:payment

# Run with coverage
npm run test:coverage
```

## 📊 Monitoring & Observability

### Health Checks
- Application: `http://localhost:3000/health`
- Metrics: `http://localhost:3000/metrics`

### Monitoring Stack
- **Prometheus**: `http://localhost:9090`
- **Grafana**: `http://localhost:3001` (admin/admin)
- **RabbitMQ Management**: `http://localhost:15672` (guest/guest)

### Key Metrics
- Application uptime and health
- HTTP request rates and response times
- Database connection pool status
- Redis memory usage
- RabbitMQ queue lengths
- MongoDB operation latencies
- Payment success/failure rates

## 🔧 Development

### Available Scripts
```bash
npm run dev          # Start development server
npm run build        # Build for production
npm run start        # Start production server
npm run lint         # Run ESLint
npm run lint:fix     # Fix linting issues
npm run migrate      # Run database migrations
npm run seed         # Run database seeders
npm run docker:build # Build Docker image
npm run docker:run   # Run Docker container
```

### Code Quality
- ESLint for code linting
- Prettier for code formatting
- Husky for git hooks
- Conventional commits

## 🚀 Deployment

### Docker Deployment
```bash
# Production build
docker build -t skyflow:latest .

# Run with environment variables
docker run -p 3000:3000 \
  -e NODE_ENV=production \
  -e DB_HOST=your-db-host \
  -e STRIPE_SECRET_KEY=your_stripe_key \
  skyflow:latest
```

### Kubernetes Deployment
```bash
# Apply Kubernetes manifests
kubectl apply -f k8s/

# Check deployment status
kubectl get pods -n skyflow
```

### CI/CD Pipeline
The project includes a comprehensive GitHub Actions workflow that:
- Runs tests and linting
- Performs security scans
- Builds and pushes Docker images
- Deploys to staging/production environments
- Sends notifications on success/failure

## 🔒 Security Features

- **JWT Authentication** - Secure token-based authentication
- **Rate Limiting** - Protection against abuse
- **CORS Configuration** - Controlled cross-origin access
- **Security Headers** - Helmet.js for security headers
- **Input Validation** - Joi schema validation
- **SQL Injection Protection** - Sequelize ORM
- **XSS Protection** - Content Security Policy
- **HTTPS Enforcement** - SSL/TLS encryption
- **Payment Security** - Stripe PCI compliance

## 📈 Performance Features

- **Redis Caching** - Fast data access
- **Database Connection Pooling** - Efficient database connections
- **Response Compression** - Reduced bandwidth usage
- **Static Asset Caching** - NGINX caching
- **Load Balancing** - NGINX upstream configuration
- **Monitoring** - Real-time performance metrics
- **Payment Optimization** - Stripe optimized checkout

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines
- Follow the existing code style
- Write tests for new features
- Update documentation
- Ensure all tests pass
- Follow conventional commit messages

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

For support and questions:
- Create an issue in the GitHub repository
- Check the documentation
- Review the troubleshooting guide

## 🗺️ Roadmap

- [ ] GraphQL API support
- [ ] WebSocket real-time updates
- [ ] Advanced analytics dashboard
- [ ] Multi-language support
- [ ] Advanced search with Elasticsearch
- [ ] Machine learning for flight predictions
- [ ] Mobile app SDK
- [ ] Third-party integrations
- [ ] Subscription-based payments
- [ ] Multi-currency support
- [ ] Payment analytics

---

**SkyFlow** - Empowering the future of airline management with modern microservices architecture and secure payment processing.
 