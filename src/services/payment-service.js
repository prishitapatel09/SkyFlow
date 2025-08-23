const stripe = require('stripe');
const { STRIPE } = require('../config/serverConfig');
const MongoService = require('./mongo-service');
const RabbitMQService = require('./rabbitmq-service');

class PaymentService {
  constructor() {
    this.stripe = stripe(STRIPE.secretKey);
    this.mongoService = new MongoService();
    this.rabbitmqService = new RabbitMQService();
    this.currency = STRIPE.currency;
    this.supportedCountries = STRIPE.supportedCountries;
  }

  /**
     * Create a payment intent for flight booking
     */
  async createPaymentIntent(paymentData) {
    try {
      const {
        amount,
        currency = this.currency,
        customerId,
        flightId,
        bookingId,
        description,
        metadata = {},
      } = paymentData;

      // Validate amount
      if (!amount || amount <= 0) {
        throw new Error('Invalid amount');
      }

      // Create payment intent
      const paymentIntent = await this.stripe.paymentIntents.create({
        amount: Math.round(amount * 100), // Convert to cents
        currency: currency.toLowerCase(),
        customer: customerId,
        description: description || `Flight booking payment - ${bookingId}`,
        metadata: {
          flightId,
          bookingId,
          type: 'flight_booking',
          ...metadata,
        },
        automatic_payment_methods: {
          enabled: true,
        },
        capture_method: 'automatic',
        confirmation_method: 'automatic',
      });

      // Log payment intent creation
      await this.logPaymentEvent('payment_intent_created', {
        paymentIntentId: paymentIntent.id,
        amount,
        currency,
        customerId,
        flightId,
        bookingId,
      });

      return {
        clientSecret: paymentIntent.client_secret,
        paymentIntentId: paymentIntent.id,
        amount: paymentIntent.amount / 100,
        currency: paymentIntent.currency,
        status: paymentIntent.status,
      };
    } catch (error) {
      console.error('Error creating payment intent:', error);
      await this.logPaymentEvent('payment_intent_error', {
        error: error.message,
        paymentData,
      });
      throw error;
    }
  }

  /**
     * Confirm a payment intent
     */
  async confirmPayment(paymentIntentId, paymentMethodId) {
    try {
      const paymentIntent = await this.stripe.paymentIntents.confirm(
        paymentIntentId,
        {
          payment_method: paymentMethodId,
        },
      );

      await this.logPaymentEvent('payment_confirmed', {
        paymentIntentId,
        paymentMethodId,
        status: paymentIntent.status,
      });

      return {
        paymentIntentId: paymentIntent.id,
        status: paymentIntent.status,
        amount: paymentIntent.amount / 100,
        currency: paymentIntent.currency,
      };
    } catch (error) {
      console.error('Error confirming payment:', error);
      await this.logPaymentEvent('payment_confirmation_error', {
        paymentIntentId,
        paymentMethodId,
        error: error.message,
      });
      throw error;
    }
  }

  /**
     * Process successful payment
     */
  async processSuccessfulPayment(paymentIntentId) {
    try {
      const paymentIntent = await this.stripe.paymentIntents.retrieve(paymentIntentId);

      if (paymentIntent.status === 'succeeded') {
        // Create payment record
        const paymentRecord = {
          paymentIntentId: paymentIntent.id,
          amount: paymentIntent.amount / 100,
          currency: paymentIntent.currency,
          status: paymentIntent.status,
          customerId: paymentIntent.customer,
          flightId: paymentIntent.metadata.flightId,
          bookingId: paymentIntent.metadata.bookingId,
          paymentMethod: paymentIntent.payment_method,
          createdAt: new Date(),
          updatedAt: new Date(),
        };

        // Save to MongoDB
        await this.mongoService.create('payments', paymentRecord);

        // Send notification
        await this.sendPaymentNotification(paymentRecord);

        // Update booking status
        await this.updateBookingStatus(paymentRecord.bookingId, 'confirmed');

        return paymentRecord;
      }
      throw new Error(`Payment not succeeded. Status: ${paymentIntent.status}`);
    } catch (error) {
      console.error('Error processing successful payment:', error);
      throw error;
    }
  }

  /**
     * Create a customer
     */
  async createCustomer(customerData) {
    try {
      const {
        email,
        name,
        phone,
        address,
        metadata = {},
      } = customerData;

      const customer = await this.stripe.customers.create({
        email,
        name,
        phone,
        address,
        metadata,
      });

      await this.logPaymentEvent('customer_created', {
        customerId: customer.id,
        email,
        name,
      });

      return {
        customerId: customer.id,
        email: customer.email,
        name: customer.name,
      };
    } catch (error) {
      console.error('Error creating customer:', error);
      throw error;
    }
  }

  /**
     * Get customer by ID
     */
  async getCustomer(customerId) {
    try {
      const customer = await this.stripe.customers.retrieve(customerId);
      return customer;
    } catch (error) {
      console.error('Error retrieving customer:', error);
      throw error;
    }
  }

  /**
     * Get payment history for a customer
     */
  async getCustomerPaymentHistory(customerId, limit = 10) {
    try {
      const payments = await this.stripe.paymentIntents.list({
        customer: customerId,
        limit,
      });

      return payments.data.map((payment) => ({
        id: payment.id,
        amount: payment.amount / 100,
        currency: payment.currency,
        status: payment.status,
        created: new Date(payment.created * 1000),
        description: payment.description,
      }));
    } catch (error) {
      console.error('Error retrieving payment history:', error);
      throw error;
    }
  }

  /**
     * Refund a payment
     */
  async refundPayment(paymentIntentId, refundData = {}) {
    try {
      const {
        amount,
        reason = 'requested_by_customer',
        metadata = {},
      } = refundData;

      const refundParams = {
        payment_intent: paymentIntentId,
        reason,
        metadata,
      };

      if (amount) {
        refundParams.amount = Math.round(amount * 100);
      }

      const refund = await this.stripe.refunds.create(refundParams);

      await this.logPaymentEvent('refund_created', {
        refundId: refund.id,
        paymentIntentId,
        amount: refund.amount / 100,
        reason,
      });

      return {
        refundId: refund.id,
        amount: refund.amount / 100,
        currency: refund.currency,
        status: refund.status,
        reason: refund.reason,
      };
    } catch (error) {
      console.error('Error creating refund:', error);
      throw error;
    }
  }

  /**
     * Get payment details
     */
  async getPaymentDetails(paymentIntentId) {
    try {
      const paymentIntent = await this.stripe.paymentIntents.retrieve(paymentIntentId);

      return {
        id: paymentIntent.id,
        amount: paymentIntent.amount / 100,
        currency: paymentIntent.currency,
        status: paymentIntent.status,
        customer: paymentIntent.customer,
        description: paymentIntent.description,
        metadata: paymentIntent.metadata,
        created: new Date(paymentIntent.created * 1000),
        lastPaymentError: paymentIntent.last_payment_error,
      };
    } catch (error) {
      console.error('Error retrieving payment details:', error);
      throw error;
    }
  }

  /**
     * Cancel a payment intent
     */
  async cancelPayment(paymentIntentId, cancellationReason = 'requested_by_customer') {
    try {
      const paymentIntent = await this.stripe.paymentIntents.cancel(
        paymentIntentId,
        {
          cancellation_reason: cancellationReason,
        },
      );

      await this.logPaymentEvent('payment_cancelled', {
        paymentIntentId,
        cancellationReason,
        status: paymentIntent.status,
      });

      return {
        paymentIntentId: paymentIntent.id,
        status: paymentIntent.status,
        cancellationReason: paymentIntent.cancellation_reason,
      };
    } catch (error) {
      console.error('Error cancelling payment:', error);
      throw error;
    }
  }

  /**
     * Handle webhook events
     */
  async handleWebhookEvent(event) {
    try {
      switch (event.type) {
      case 'payment_intent.succeeded':
        await this.processSuccessfulPayment(event.data.object.id);
        break;

      case 'payment_intent.payment_failed':
        await this.handlePaymentFailure(event.data.object);
        break;

      case 'charge.refunded':
        await this.handleRefund(event.data.object);
        break;

      case 'customer.subscription.created':
        await this.handleSubscriptionCreated(event.data.object);
        break;

      default:
        console.log(`Unhandled event type: ${event.type}`);
      }

      await this.logPaymentEvent('webhook_processed', {
        eventType: event.type,
        eventId: event.id,
      });
    } catch (error) {
      console.error('Error handling webhook event:', error);
      throw error;
    }
  }

  /**
     * Handle payment failure
     */
  async handlePaymentFailure(paymentIntent) {
    try {
      await this.logPaymentEvent('payment_failed', {
        paymentIntentId: paymentIntent.id,
        lastPaymentError: paymentIntent.last_payment_error,
      });

      // Send failure notification
      await this.sendPaymentFailureNotification(paymentIntent);

      // Update booking status
      if (paymentIntent.metadata.bookingId) {
        await this.updateBookingStatus(paymentIntent.metadata.bookingId, 'payment_failed');
      }
    } catch (error) {
      console.error('Error handling payment failure:', error);
    }
  }

  /**
     * Handle refund
     */
  async handleRefund(refund) {
    try {
      await this.logPaymentEvent('refund_processed', {
        refundId: refund.id,
        paymentIntentId: refund.payment_intent,
        amount: refund.amount / 100,
      });

      // Send refund notification
      await this.sendRefundNotification(refund);
    } catch (error) {
      console.error('Error handling refund:', error);
    }
  }

  /**
     * Handle subscription created
     */
  async handleSubscriptionCreated(subscription) {
    try {
      await this.logPaymentEvent('subscription_created', {
        subscriptionId: subscription.id,
        customerId: subscription.customer,
        status: subscription.status,
      });
    } catch (error) {
      console.error('Error handling subscription created:', error);
    }
  }

  /**
     * Log payment events
     */
  async logPaymentEvent(eventType, eventData) {
    try {
      await this.mongoService.create('payment_events', {
        eventType,
        eventData,
        timestamp: new Date(),
      });
    } catch (error) {
      console.error('Error logging payment event:', error);
    }
  }

  /**
     * Send payment notification
     */
  async sendPaymentNotification(paymentRecord) {
    try {
      await this.rabbitmqService.publish({
        type: 'PAYMENT_SUCCESS',
        data: {
          customerId: paymentRecord.customerId,
          bookingId: paymentRecord.bookingId,
          amount: paymentRecord.amount,
          currency: paymentRecord.currency,
        },
        timestamp: new Date(),
      });
    } catch (error) {
      console.error('Error sending payment notification:', error);
    }
  }

  /**
     * Send payment failure notification
     */
  async sendPaymentFailureNotification(paymentIntent) {
    try {
      await this.rabbitmqService.publish({
        type: 'PAYMENT_FAILED',
        data: {
          customerId: paymentIntent.customer,
          bookingId: paymentIntent.metadata.bookingId,
          error: paymentIntent.last_payment_error?.message,
        },
        timestamp: new Date(),
      });
    } catch (error) {
      console.error('Error sending payment failure notification:', error);
    }
  }

  /**
     * Send refund notification
     */
  async sendRefundNotification(refund) {
    try {
      await this.rabbitmqService.publish({
        type: 'REFUND_PROCESSED',
        data: {
          refundId: refund.id,
          paymentIntentId: refund.payment_intent,
          amount: refund.amount / 100,
          currency: refund.currency,
        },
        timestamp: new Date(),
      });
    } catch (error) {
      console.error('Error sending refund notification:', error);
    }
  }

  /**
     * Update booking status
     */
  async updateBookingStatus(bookingId, status) {
    try {
      // This would typically update the booking in your database
      // For now, we'll just log it
      console.log(`Updating booking ${bookingId} status to ${status}`);

      // In a real implementation, you would update the booking record
      // await bookingRepository.updateStatus(bookingId, status);
    } catch (error) {
      console.error('Error updating booking status:', error);
    }
  }

  /**
     * Get supported payment methods
     */
  getSupportedPaymentMethods() {
    return STRIPE.paymentMethods;
  }

  /**
     * Get supported countries
     */
  getSupportedCountries() {
    return this.supportedCountries;
  }

  /**
     * Validate payment data
     */
  validatePaymentData(paymentData) {
    const { amount, currency, customerId } = paymentData;

    if (!amount || amount <= 0) {
      throw new Error('Invalid amount');
    }

    if (!customerId) {
      throw new Error('Customer ID is required');
    }

    if (currency && !['usd', 'inr', 'eur', 'gbp'].includes(currency.toLowerCase())) {
      throw new Error('Unsupported currency');
    }

    return true;
  }
}

module.exports = PaymentService;
