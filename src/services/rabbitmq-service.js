const amqp = require('amqplib');
const { RABBITMQ } = require('../config/serverConfig');

class RabbitMQService {
  constructor() {
    this.channel = null;
    this.connection = null;
    this.isConnected = false;
  }

  async connect() {
    try {
      this.connection = await amqp.connect(RABBITMQ.url);
      this.channel = await this.connection.createChannel();
      this.isConnected = true;

      this.connection.on('close', () => {
        this.isConnected = false;
        console.log('RabbitMQ connection closed');
      });

      this.connection.on('error', (error) => {
        this.isConnected = false;
        console.log('RabbitMQ connection error:', error);
      });

      await this.channel.assertQueue('notifications');
      console.log('RabbitMQ connected successfully');
    } catch (error) {
      console.log('Something went wrong in RabbitMQ connection:', error);
      this.isConnected = false;
      throw error;
    }
  }

  async publish(message) {
    try {
      if (!this.channel) {
        await this.connect();
      }
      await this.channel.sendToQueue(
        'notifications',
        Buffer.from(JSON.stringify(message)),
      );
    } catch (error) {
      console.log('Something went wrong in RabbitMQ publish:', error);
      throw error;
    }
  }

  async consume(callback) {
    try {
      if (!this.channel) {
        await this.connect();
      }
      await this.channel.consume('notifications', (msg) => {
        if (msg) {
          try {
            const parsedMessage = JSON.parse(msg.content.toString());
            callback(parsedMessage);
            this.channel.ack(msg);
          } catch (error) {
            console.log('Error processing message:', error);
            this.channel.nack(msg);
          }
        }
      });
    } catch (error) {
      console.log('Something went wrong in RabbitMQ consume:', error);
      throw error;
    }
  }

  async close() {
    try {
      if (this.connection) {
        await this.connection.close();
        this.isConnected = false;
      }
    } catch (error) {
      console.log('Something went wrong in RabbitMQ close:', error);
      throw error;
    }
  }
}

module.exports = RabbitMQService;
