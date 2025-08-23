const { expect } = require('chai');
const sinon = require('sinon');
const PaymentService = require('../services/payment-service');

describe('PaymentService', () => {
  let paymentService;
  let stripeStub;
  let mongoServiceStub;
  let rabbitmqServiceStub;

  beforeEach(() => {
    // Create stubs for dependencies
    stripeStub = {
      paymentIntents: {
        create: sinon.stub(),
        confirm: sinon.stub(),
        retrieve: sinon.stub(),
        cancel: sinon.stub(),
        list: sinon.stub(),
      },
      customers: {
        create: sinon.stub(),
        retrieve: sinon.stub(),
      },
      refunds: {
        create: sinon.stub(),
      },
      webhooks: {
        constructEvent: sinon.stub(),
      },
    };

    mongoServiceStub = {
      create: sinon.stub(),
      find: sinon.stub(),
      findOne: sinon.stub(),
      update: sinon.stub(),
      delete: sinon.stub(),
    };

    rabbitmqServiceStub = {
      publish: sinon.stub(),
    };

    // Create payment service with stubbed dependencies
    paymentService = new PaymentService();
    paymentService.stripe = stripeStub;
    paymentService.mongoService = mongoServiceStub;
    paymentService.rabbitmqService = rabbitmqServiceStub;
  });

  afterEach(() => {
    sinon.restore();
  });

  describe('createPaymentIntent', () => {
    it('should create a payment intent successfully', async () => {
      const paymentData = {
        amount: 100.50,
        currency: 'usd',
        customerId: 'cus_123',
        flightId: 'flight_123',
        bookingId: 'booking_123',
        description: 'Flight booking payment',
      };

      const mockPaymentIntent = {
        id: 'pi_123',
        client_secret: 'pi_123_secret',
        amount: 10050,
        currency: 'usd',
        status: 'requires_payment_method',
      };

      stripeStub.paymentIntents.create.resolves(mockPaymentIntent);
      mongoServiceStub.create.resolves({});

      const result = await paymentService.createPaymentIntent(paymentData);

      expect(result).to.have.property('clientSecret', 'pi_123_secret');
      expect(result).to.have.property('paymentIntentId', 'pi_123');
      expect(result).to.have.property('amount', 100.50);
      expect(result).to.have.property('currency', 'usd');
      expect(result).to.have.property('status', 'requires_payment_method');

      expect(stripeStub.paymentIntents.create.calledOnce).to.be.true;
      expect(mongoServiceStub.create.calledOnce).to.be.true;
    });

    it('should throw error for invalid amount', async () => {
      const paymentData = {
        amount: -100,
        customerId: 'cus_123',
        flightId: 'flight_123',
        bookingId: 'booking_123',
      };

      try {
        await paymentService.createPaymentIntent(paymentData);
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.equal('Invalid amount');
      }
    });

    it('should handle Stripe API errors', async () => {
      const paymentData = {
        amount: 100,
        customerId: 'cus_123',
        flightId: 'flight_123',
        bookingId: 'booking_123',
      };

      stripeStub.paymentIntents.create.rejects(new Error('Stripe API error'));

      try {
        await paymentService.createPaymentIntent(paymentData);
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.equal('Stripe API error');
      }
    });
  });

  describe('confirmPayment', () => {
    it('should confirm a payment successfully', async () => {
      const paymentIntentId = 'pi_123';
      const paymentMethodId = 'pm_123';

      const mockPaymentIntent = {
        id: 'pi_123',
        status: 'succeeded',
        amount: 10050,
        currency: 'usd',
      };

      stripeStub.paymentIntents.confirm.resolves(mockPaymentIntent);
      mongoServiceStub.create.resolves({});

      const result = await paymentService.confirmPayment(paymentIntentId, paymentMethodId);

      expect(result).to.have.property('paymentIntentId', 'pi_123');
      expect(result).to.have.property('status', 'succeeded');
      expect(result).to.have.property('amount', 100.50);
      expect(result).to.have.property('currency', 'usd');

      expect(stripeStub.paymentIntents.confirm.calledOnce).to.be.true;
    });
  });

  describe('createCustomer', () => {
    it('should create a customer successfully', async () => {
      const customerData = {
        email: 'test@example.com',
        name: 'John Doe',
        phone: '+1234567890',
      };

      const mockCustomer = {
        id: 'cus_123',
        email: 'test@example.com',
        name: 'John Doe',
      };

      stripeStub.customers.create.resolves(mockCustomer);
      mongoServiceStub.create.resolves({});

      const result = await paymentService.createCustomer(customerData);

      expect(result).to.have.property('customerId', 'cus_123');
      expect(result).to.have.property('email', 'test@example.com');
      expect(result).to.have.property('name', 'John Doe');

      expect(stripeStub.customers.create.calledOnce).to.be.true;
    });
  });

  describe('getCustomer', () => {
    it('should retrieve a customer successfully', async () => {
      const customerId = 'cus_123';
      const mockCustomer = {
        id: 'cus_123',
        email: 'test@example.com',
        name: 'John Doe',
      };

      stripeStub.customers.retrieve.resolves(mockCustomer);

      const result = await paymentService.getCustomer(customerId);

      expect(result).to.deep.equal(mockCustomer);
      expect(stripeStub.customers.retrieve.calledOnce).to.be.true;
    });
  });

  describe('getCustomerPaymentHistory', () => {
    it('should retrieve customer payment history successfully', async () => {
      const customerId = 'cus_123';
      const limit = 5;

      const mockPayments = {
        data: [
          {
            id: 'pi_1',
            amount: 10050,
            currency: 'usd',
            status: 'succeeded',
            created: 1640995200,
            description: 'Flight booking',
          },
          {
            id: 'pi_2',
            amount: 20000,
            currency: 'usd',
            status: 'succeeded',
            created: 1640995200,
            description: 'Flight booking',
          },
        ],
      };

      stripeStub.paymentIntents.list.resolves(mockPayments);

      const result = await paymentService.getCustomerPaymentHistory(customerId, limit);

      expect(result).to.be.an('array');
      expect(result).to.have.length(2);
      expect(result[0]).to.have.property('id', 'pi_1');
      expect(result[0]).to.have.property('amount', 100.50);

      expect(stripeStub.paymentIntents.list.calledOnce).to.be.true;
    });
  });

  describe('refundPayment', () => {
    it('should create a refund successfully', async () => {
      const paymentIntentId = 'pi_123';
      const refundData = {
        amount: 50.25,
        reason: 'requested_by_customer',
      };

      const mockRefund = {
        id: 're_123',
        amount: 5025,
        currency: 'usd',
        status: 'succeeded',
        reason: 'requested_by_customer',
      };

      stripeStub.refunds.create.resolves(mockRefund);
      mongoServiceStub.create.resolves({});

      const result = await paymentService.refundPayment(paymentIntentId, refundData);

      expect(result).to.have.property('refundId', 're_123');
      expect(result).to.have.property('amount', 50.25);
      expect(result).to.have.property('currency', 'usd');
      expect(result).to.have.property('status', 'succeeded');
      expect(result).to.have.property('reason', 'requested_by_customer');

      expect(stripeStub.refunds.create.calledOnce).to.be.true;
    });
  });

  describe('getPaymentDetails', () => {
    it('should retrieve payment details successfully', async () => {
      const paymentIntentId = 'pi_123';

      const mockPaymentIntent = {
        id: 'pi_123',
        amount: 10050,
        currency: 'usd',
        status: 'succeeded',
        customer: 'cus_123',
        description: 'Flight booking',
        metadata: { flightId: 'flight_123' },
        created: 1640995200,
      };

      stripeStub.paymentIntents.retrieve.resolves(mockPaymentIntent);

      const result = await paymentService.getPaymentDetails(paymentIntentId);

      expect(result).to.have.property('id', 'pi_123');
      expect(result).to.have.property('amount', 100.50);
      expect(result).to.have.property('currency', 'usd');
      expect(result).to.have.property('status', 'succeeded');
      expect(result).to.have.property('customer', 'cus_123');

      expect(stripeStub.paymentIntents.retrieve.calledOnce).to.be.true;
    });
  });

  describe('cancelPayment', () => {
    it('should cancel a payment successfully', async () => {
      const paymentIntentId = 'pi_123';
      const cancellationReason = 'requested_by_customer';

      const mockPaymentIntent = {
        id: 'pi_123',
        status: 'canceled',
        cancellation_reason: 'requested_by_customer',
      };

      stripeStub.paymentIntents.cancel.resolves(mockPaymentIntent);
      mongoServiceStub.create.resolves({});

      const result = await paymentService.cancelPayment(paymentIntentId, cancellationReason);

      expect(result).to.have.property('paymentIntentId', 'pi_123');
      expect(result).to.have.property('status', 'canceled');
      expect(result).to.have.property('cancellationReason', 'requested_by_customer');

      expect(stripeStub.paymentIntents.cancel.calledOnce).to.be.true;
    });
  });

  describe('processSuccessfulPayment', () => {
    it('should process successful payment correctly', async () => {
      const paymentIntentId = 'pi_123';

      const mockPaymentIntent = {
        id: 'pi_123',
        amount: 10050,
        currency: 'usd',
        status: 'succeeded',
        customer: 'cus_123',
        payment_method: 'pm_123',
        metadata: {
          flightId: 'flight_123',
          bookingId: 'booking_123',
        },
      };

      stripeStub.paymentIntents.retrieve.resolves(mockPaymentIntent);
      mongoServiceStub.create.resolves({});
      rabbitmqServiceStub.publish.resolves();

      const result = await paymentService.processSuccessfulPayment(paymentIntentId);

      expect(result).to.have.property('paymentIntentId', 'pi_123');
      expect(result).to.have.property('amount', 100.50);
      expect(result).to.have.property('currency', 'usd');
      expect(result).to.have.property('status', 'succeeded');
      expect(result).to.have.property('customerId', 'cus_123');
      expect(result).to.have.property('flightId', 'flight_123');
      expect(result).to.have.property('bookingId', 'booking_123');

      expect(stripeStub.paymentIntents.retrieve.calledOnce).to.be.true;
      expect(mongoServiceStub.create.calledOnce).to.be.true;
      expect(rabbitmqServiceStub.publish.calledOnce).to.be.true;
    });

    it('should throw error for non-succeeded payment', async () => {
      const paymentIntentId = 'pi_123';

      const mockPaymentIntent = {
        id: 'pi_123',
        status: 'requires_payment_method',
      };

      stripeStub.paymentIntents.retrieve.resolves(mockPaymentIntent);

      try {
        await paymentService.processSuccessfulPayment(paymentIntentId);
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.include('Payment not succeeded');
      }
    });
  });

  describe('handleWebhookEvent', () => {
    it('should handle payment_intent.succeeded event', async () => {
      const event = {
        type: 'payment_intent.succeeded',
        data: {
          object: {
            id: 'pi_123',
          },
        },
      };

      const mockPaymentIntent = {
        id: 'pi_123',
        amount: 10050,
        currency: 'usd',
        status: 'succeeded',
        customer: 'cus_123',
        payment_method: 'pm_123',
        metadata: {
          flightId: 'flight_123',
          bookingId: 'booking_123',
        },
      };

      stripeStub.paymentIntents.retrieve.resolves(mockPaymentIntent);
      mongoServiceStub.create.resolves({});
      rabbitmqServiceStub.publish.resolves();

      await paymentService.handleWebhookEvent(event);

      expect(stripeStub.paymentIntents.retrieve.calledOnce).to.be.true;
      expect(mongoServiceStub.create.called).to.be.true;
    });

    it('should handle payment_intent.payment_failed event', async () => {
      const event = {
        type: 'payment_intent.payment_failed',
        data: {
          object: {
            id: 'pi_123',
            last_payment_error: { message: 'Card declined' },
            metadata: { bookingId: 'booking_123' },
          },
        },
      };

      mongoServiceStub.create.resolves({});
      rabbitmqServiceStub.publish.resolves();

      await paymentService.handleWebhookEvent(event);

      expect(mongoServiceStub.create.called).to.be.true;
      expect(rabbitmqServiceStub.publish.called).to.be.true;
    });

    it('should handle unhandled event types', async () => {
      const event = {
        type: 'unknown.event.type',
        data: {
          object: {},
        },
      };

      mongoServiceStub.create.resolves({});

      await paymentService.handleWebhookEvent(event);

      expect(mongoServiceStub.create.calledOnce).to.be.true;
    });
  });

  describe('validatePaymentData', () => {
    it('should validate correct payment data', () => {
      const paymentData = {
        amount: 100,
        currency: 'usd',
        customerId: 'cus_123',
      };

      const result = paymentService.validatePaymentData(paymentData);
      expect(result).to.be.true;
    });

    it('should throw error for invalid amount', () => {
      const paymentData = {
        amount: -100,
        currency: 'usd',
        customerId: 'cus_123',
      };

      try {
        paymentService.validatePaymentData(paymentData);
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.equal('Invalid amount');
      }
    });

    it('should throw error for missing customer ID', () => {
      const paymentData = {
        amount: 100,
        currency: 'usd',
      };

      try {
        paymentService.validatePaymentData(paymentData);
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.equal('Customer ID is required');
      }
    });

    it('should throw error for unsupported currency', () => {
      const paymentData = {
        amount: 100,
        currency: 'xyz',
        customerId: 'cus_123',
      };

      try {
        paymentService.validatePaymentData(paymentData);
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.equal('Unsupported currency');
      }
    });
  });

  describe('getSupportedPaymentMethods', () => {
    it('should return supported payment methods', () => {
      const methods = paymentService.getSupportedPaymentMethods();
      expect(methods).to.be.an('array');
      expect(methods).to.include('card');
      expect(methods).to.include('upi');
      expect(methods).to.include('netbanking');
    });
  });

  describe('getSupportedCountries', () => {
    it('should return supported countries', () => {
      const countries = paymentService.getSupportedCountries();
      expect(countries).to.be.an('array');
      expect(countries).to.include('US');
      expect(countries).to.include('IN');
      expect(countries).to.include('CA');
      expect(countries).to.include('GB');
      expect(countries).to.include('AU');
    });
  });
});
