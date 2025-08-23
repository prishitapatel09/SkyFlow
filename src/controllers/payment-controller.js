const Joi = require('joi');
const PaymentService = require('../services/payment-service');
const { ClientErrorCodes } = require('../utils/error-codes');
const errorResponse = require('../utils/error-response');

const paymentService = new PaymentService();

// Validation schemas
const createPaymentIntentSchema = Joi.object({
  amount: Joi.number().positive().required(),
  currency: Joi.string().valid('usd', 'inr', 'eur', 'gbp').default('usd'),
  customerId: Joi.string().required(),
  flightId: Joi.string().required(),
  bookingId: Joi.string().required(),
  description: Joi.string().optional(),
  metadata: Joi.object().optional(),
});

const confirmPaymentSchema = Joi.object({
  paymentMethodId: Joi.string().required(),
});

const createCustomerSchema = Joi.object({
  email: Joi.string().email().required(),
  name: Joi.string().required(),
  phone: Joi.string().optional(),
  address: Joi.object({
    line1: Joi.string().optional(),
    line2: Joi.string().optional(),
    city: Joi.string().optional(),
    state: Joi.string().optional(),
    postal_code: Joi.string().optional(),
    country: Joi.string().optional(),
  }).optional(),
  metadata: Joi.object().optional(),
});

const refundPaymentSchema = Joi.object({
  amount: Joi.number().positive().optional(),
  reason: Joi.string().valid('requested_by_customer', 'duplicate', 'fraudulent').default('requested_by_customer'),
  metadata: Joi.object().optional(),
});

/**
 * Create a payment intent
 */
const createPaymentIntent = async (req, res) => {
  try {
    // Validate request body
    const { error, value } = createPaymentIntentSchema.validate(req.body);
    if (error) {
      return res.status(ClientErrorCodes.BAD_REQUEST).json({
        success: false,
        message: 'Validation error',
        error: error.details[0].message,
      });
    }

    const paymentIntent = await paymentService.createPaymentIntent(value);

    return res.status(ClientErrorCodes.CREATED).json({
      success: true,
      message: 'Payment intent created successfully',
      data: paymentIntent,
    });
  } catch (error) {
    console.error('Error creating payment intent:', error);
    return res.status(500).json(
      errorResponse.create('Failed to create payment intent', error),
    );
  }
};

/**
 * Confirm a payment
 */
const confirmPayment = async (req, res) => {
  try {
    const { paymentIntentId } = req.params;

    // Validate request body
    const { error, value } = confirmPaymentSchema.validate(req.body);
    if (error) {
      return res.status(ClientErrorCodes.BAD_REQUEST).json({
        success: false,
        message: 'Validation error',
        error: error.details[0].message,
      });
    }

    const result = await paymentService.confirmPayment(paymentIntentId, value.paymentMethodId);

    return res.status(200).json({
      success: true,
      message: 'Payment confirmed successfully',
      data: result,
    });
  } catch (error) {
    console.error('Error confirming payment:', error);
    return res.status(500).json(
      errorResponse.create('Failed to confirm payment', error),
    );
  }
};

/**
 * Create a customer
 */
const createCustomer = async (req, res) => {
  try {
    // Validate request body
    const { error, value } = createCustomerSchema.validate(req.body);
    if (error) {
      return res.status(ClientErrorCodes.BAD_REQUEST).json({
        success: false,
        message: 'Validation error',
        error: error.details[0].message,
      });
    }

    const customer = await paymentService.createCustomer(value);

    return res.status(ClientErrorCodes.CREATED).json({
      success: true,
      message: 'Customer created successfully',
      data: customer,
    });
  } catch (error) {
    console.error('Error creating customer:', error);
    return res.status(500).json(
      errorResponse.create('Failed to create customer', error),
    );
  }
};

/**
 * Get customer details
 */
const getCustomer = async (req, res) => {
  try {
    const { customerId } = req.params;
    const customer = await paymentService.getCustomer(customerId);

    return res.status(200).json({
      success: true,
      message: 'Customer retrieved successfully',
      data: customer,
    });
  } catch (error) {
    console.error('Error retrieving customer:', error);
    return res.status(500).json(
      errorResponse.create('Failed to retrieve customer', error),
    );
  }
};

/**
 * Get customer payment history
 */
const getCustomerPaymentHistory = async (req, res) => {
  try {
    const { customerId } = req.params;
    const { limit = 10 } = req.query;

    const payments = await paymentService.getCustomerPaymentHistory(customerId, parseInt(limit));

    return res.status(200).json({
      success: true,
      message: 'Payment history retrieved successfully',
      data: payments,
    });
  } catch (error) {
    console.error('Error retrieving payment history:', error);
    return res.status(500).json(
      errorResponse.create('Failed to retrieve payment history', error),
    );
  }
};

/**
 * Get payment details
 */
const getPaymentDetails = async (req, res) => {
  try {
    const { paymentIntentId } = req.params;
    const payment = await paymentService.getPaymentDetails(paymentIntentId);

    return res.status(200).json({
      success: true,
      message: 'Payment details retrieved successfully',
      data: payment,
    });
  } catch (error) {
    console.error('Error retrieving payment details:', error);
    return res.status(500).json(
      errorResponse.create('Failed to retrieve payment details', error),
    );
  }
};

/**
 * Refund a payment
 */
const refundPayment = async (req, res) => {
  try {
    const { paymentIntentId } = req.params;

    // Validate request body
    const { error, value } = refundPaymentSchema.validate(req.body);
    if (error) {
      return res.status(ClientErrorCodes.BAD_REQUEST).json({
        success: false,
        message: 'Validation error',
        error: error.details[0].message,
      });
    }

    const refund = await paymentService.refundPayment(paymentIntentId, value);

    return res.status(200).json({
      success: true,
      message: 'Refund processed successfully',
      data: refund,
    });
  } catch (error) {
    console.error('Error processing refund:', error);
    return res.status(500).json(
      errorResponse.create('Failed to process refund', error),
    );
  }
};

/**
 * Cancel a payment
 */
const cancelPayment = async (req, res) => {
  try {
    const { paymentIntentId } = req.params;
    const { reason = 'requested_by_customer' } = req.body;

    const result = await paymentService.cancelPayment(paymentIntentId, reason);

    return res.status(200).json({
      success: true,
      message: 'Payment cancelled successfully',
      data: result,
    });
  } catch (error) {
    console.error('Error cancelling payment:', error);
    return res.status(500).json(
      errorResponse.create('Failed to cancel payment', error),
    );
  }
};

/**
 * Handle Stripe webhook
 */
const handleWebhook = async (req, res) => {
  try {
    const sig = req.headers['stripe-signature'];
    const { STRIPE } = require('../config/serverConfig');

    let event;

    try {
      event = stripe.webhooks.constructEvent(req.body, sig, STRIPE.webhookSecret);
    } catch (err) {
      console.error('Webhook signature verification failed:', err.message);
      return res.status(400).send(`Webhook Error: ${err.message}`);
    }

    // Handle the event
    await paymentService.handleWebhookEvent(event);

    res.json({ received: true });
  } catch (error) {
    console.error('Error handling webhook:', error);
    return res.status(500).json(
      errorResponse.create('Failed to handle webhook', error),
    );
  }
};

/**
 * Get supported payment methods
 */
const getSupportedPaymentMethods = async (req, res) => {
  try {
    const paymentMethods = paymentService.getSupportedPaymentMethods();
    const countries = paymentService.getSupportedCountries();

    return res.status(200).json({
      success: true,
      message: 'Payment methods retrieved successfully',
      data: {
        paymentMethods,
        countries,
      },
    });
  } catch (error) {
    console.error('Error retrieving payment methods:', error);
    return res.status(500).json(
      errorResponse.create('Failed to retrieve payment methods', error),
    );
  }
};

/**
 * Process successful payment (internal use)
 */
const processSuccessfulPayment = async (req, res) => {
  try {
    const { paymentIntentId } = req.params;
    const result = await paymentService.processSuccessfulPayment(paymentIntentId);

    return res.status(200).json({
      success: true,
      message: 'Payment processed successfully',
      data: result,
    });
  } catch (error) {
    console.error('Error processing payment:', error);
    return res.status(500).json(
      errorResponse.create('Failed to process payment', error),
    );
  }
};

module.exports = {
  createPaymentIntent,
  confirmPayment,
  createCustomer,
  getCustomer,
  getCustomerPaymentHistory,
  getPaymentDetails,
  refundPayment,
  cancelPayment,
  handleWebhook,
  getSupportedPaymentMethods,
  processSuccessfulPayment,
};
