#!/bin/bash

echo "Starting SkyFlow development environment..."

# Check if .env file exists
if [ ! -f .env ]; then
    echo "Creating .env file from template..."
    cat > .env << EOF
# Server Configuration
PORT=3000
NODE_ENV=development

# Database Configuration
DB_HOST=localhost
DB_USER=root
DB_PASSWORD=password
DB_NAME=flight_search

# Redis Configuration
REDIS_URL=redis://localhost:6379

# RabbitMQ Configuration
RABBITMQ_URL=amqp://localhost

# Email Configuration (for Gmail)
EMAIL_USER=your-email@gmail.com
EMAIL_PASSWORD=your-app-password

# Database Sync (set to true to auto-sync database schema)
SYNC_DB=false
EOF
    echo ".env file created. Please update with your actual credentials."
fi

# Install dependencies if node_modules doesn't exist
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
fi

# Start the application
echo "Starting the application..."
npm start
