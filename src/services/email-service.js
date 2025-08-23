const nodemailer = require('nodemailer');
const RabbitMQService = require('./rabbitmq-service');
const { EMAIL } = require('../config/serverConfig');

class EmailService {
  constructor() {
    this.rabbitmq = new RabbitMQService();
    this.transporter = null;
    this.initializeTransporter();
  }

  initializeTransporter() {
    if (EMAIL.user && EMAIL.password) {
      this.transporter = nodemailer.createTransport({
        service: 'gmail',
        auth: {
          user: EMAIL.user,
          pass: EMAIL.password,
        },
      });
    } else {
      console.warn('Email credentials not configured. Email service will not work.');
    }
  }

  async setupConsumer() {
    try {
      await this.rabbitmq.connect();
      await this.rabbitmq.consume(async (message) => {
        if (message.type === 'REMINDER') {
          await this.sendReminderEmail(message.data);
        }
      });
    } catch (error) {
      console.error('Failed to setup email consumer:', error);
    }
  }

  async sendReminderEmail(data) {
    try {
      if (!this.transporter) {
        console.warn('Email transporter not configured');
        return;
      }

      if (!data.email || !data.flightNumber || !data.departureTime) {
        console.warn('Missing required email data:', data);
        return;
      }

      await this.transporter.sendMail({
        from: EMAIL.user,
        to: data.email,
        subject: 'Flight Reminder',
        text: `Your flight ${data.flightNumber} is scheduled for ${data.departureTime}`,
      });

      console.log(`Reminder email sent to ${data.email} for flight ${data.flightNumber}`);
    } catch (error) {
      console.error('Error sending email:', error);
    }
  }

  async sendEmail(to, subject, text, html = null) {
    try {
      if (!this.transporter) {
        throw new Error('Email transporter not configured');
      }

      const mailOptions = {
        from: EMAIL.user,
        to,
        subject,
        text,
      };

      if (html) {
        mailOptions.html = html;
      }

      await this.transporter.sendMail(mailOptions);
      console.log(`Email sent to ${to}`);
    } catch (error) {
      console.error('Error sending email:', error);
      throw error;
    }
  }
}

module.exports = EmailService;
