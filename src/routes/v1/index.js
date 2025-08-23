const express = require('express');
const { FlightMiddlewares } = require('../../middlewares/index');
const CityController = require('../../controllers/city-controller');
const FlightController = require('../../controllers/flight-controller');
const AirportController = require('../../controllers/airport-controller');
const PaymentController = require('../../controllers/payment-controller');

const router = express.Router();

// City routes
router.post('/city', CityController.create);
router.delete('/city/:id', CityController.destroy);
router.get('/city/:id', CityController.get);
router.get('/city', CityController.getAll);
router.patch('/city/:id', CityController.update);

// Flight routes
router.post('/flights/', FlightMiddlewares.validateCreateFlight, FlightController.create);
router.get('/flights/', FlightController.getAll);
router.get('/flights/:id', FlightController.get);
router.patch('/flights/:id', FlightController.update);

// Airport routes
router.post('/airports/', AirportController.create);

// Payment routes
router.post('/payments/intent', PaymentController.createPaymentIntent);
router.post('/payments/:paymentIntentId/confirm', PaymentController.confirmPayment);
router.get('/payments/:paymentIntentId', PaymentController.getPaymentDetails);
router.post('/payments/:paymentIntentId/refund', PaymentController.refundPayment);
router.post('/payments/:paymentIntentId/cancel', PaymentController.cancelPayment);
router.post('/payments/:paymentIntentId/process', PaymentController.processSuccessfulPayment);

// Customer routes
router.post('/customers', PaymentController.createCustomer);
router.get('/customers/:customerId', PaymentController.getCustomer);
router.get('/customers/:customerId/payments', PaymentController.getCustomerPaymentHistory);

// Payment configuration routes
router.get('/payments/methods', PaymentController.getSupportedPaymentMethods);

// Webhook route (no authentication required)
router.post('/webhooks/stripe', express.raw({ type: 'application/json' }), PaymentController.handleWebhook);

module.exports = router;
