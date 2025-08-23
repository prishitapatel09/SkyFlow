# Use official Node.js image with Alpine for smaller size
FROM node:18-alpine

# Set working directory
WORKDIR /usr/src/app

# Install system dependencies for Redis and build tools
RUN apk add --no-cache python3 make g++

# Copy package files
COPY package*.json ./

# Install app dependencies with clean cache
RUN npm install --production && \
    npm cache clean --force

# Copy source code
COPY . .

# Build the application if needed
RUN npm run build

# Expose ports
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 \
    CMD node healthcheck.js

# Start the application using node directly for better signal handling
CMD ["node", "src/index.js"]