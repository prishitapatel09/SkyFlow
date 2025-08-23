const { expect } = require('chai');
const MongoService = require('../services/mongo-service');

describe('MongoDB Service', () => {
  let mongoService;

  before(async () => {
    mongoService = new MongoService();
    await mongoService.connect();
  });

  after(async () => {
    await mongoService.disconnect();
  });

  describe('Connection', () => {
    it('should connect to MongoDB successfully', async () => {
      const isConnected = await mongoService.isConnected();
      expect(isConnected).to.be.true;
    });
  });

  describe('CRUD Operations', () => {
    const testCollection = 'test_collection';
    const testData = { name: 'Test Item', value: 123 };

    afterEach(async () => {
      // Clean up test data
      await mongoService.delete(testCollection, {});
    });

    it('should create a document', async () => {
      const result = await mongoService.create(testCollection, testData);
      expect(result).to.have.property('_id');
      expect(result.name).to.equal(testData.name);
      expect(result.value).to.equal(testData.value);
    });

    it('should find documents', async () => {
      await mongoService.create(testCollection, testData);
      const results = await mongoService.find(testCollection, { name: testData.name });
      expect(results).to.be.an('array');
      expect(results).to.have.length(1);
      expect(results[0].name).to.equal(testData.name);
    });

    it('should find one document', async () => {
      const created = await mongoService.create(testCollection, testData);
      const result = await mongoService.findOne(testCollection, { _id: created._id });
      expect(result).to.have.property('_id');
      expect(result.name).to.equal(testData.name);
    });

    it('should update documents', async () => {
      const created = await mongoService.create(testCollection, testData);
      const updateData = { value: 456 };

      await mongoService.update(testCollection, { _id: created._id }, updateData);
      const updated = await mongoService.findOne(testCollection, { _id: created._id });

      expect(updated.value).to.equal(updateData.value);
    });

    it('should delete documents', async () => {
      const created = await mongoService.create(testCollection, testData);
      await mongoService.delete(testCollection, { _id: created._id });

      const result = await mongoService.findOne(testCollection, { _id: created._id });
      expect(result).to.be.null;
    });
  });

  describe('User Sessions', () => {
    const testUserId = 'test-user-123';
    const testSessionData = { preferences: { theme: 'dark' } };

    afterEach(async () => {
      await mongoService.delete('user_sessions', { userId: testUserId });
    });

    it('should create user session', async () => {
      const session = await mongoService.createUserSession(testUserId, testSessionData);
      expect(session).to.have.property('sessionId');
      expect(session.userId).to.equal(testUserId);
      expect(session.data).to.deep.equal(testSessionData);
      expect(session).to.have.property('expiresAt');
    });

    it('should get user session', async () => {
      const created = await mongoService.createUserSession(testUserId, testSessionData);
      const session = await mongoService.getUserSession(created.sessionId);
      expect(session).to.have.property('sessionId', created.sessionId);
      expect(session.userId).to.equal(testUserId);
    });
  });

  describe('Audit Logs', () => {
    const testAction = 'TEST_ACTION';
    const testUserId = 'test-user-123';
    const testResource = '/api/test';
    const testDetails = { method: 'GET' };

    afterEach(async () => {
      await mongoService.delete('audit_logs', { action: testAction });
    });

    it('should create audit log', async () => {
      const log = await mongoService.createAuditLog(
        testAction,
        testUserId,
        testResource,
        testDetails,
        '127.0.0.1',
        'Test User Agent',
      );

      expect(log.action).to.equal(testAction);
      expect(log.userId).to.equal(testUserId);
      expect(log.resource).to.equal(testResource);
      expect(log.details).to.deep.equal(testDetails);
    });
  });

  describe('Analytics', () => {
    const testEvent = 'TEST_EVENT';
    const testUserId = 'test-user-123';
    const testData = { page: '/test', duration: 5000 };

    afterEach(async () => {
      await mongoService.delete('analytics', { event: testEvent });
    });

    it('should create analytics event', async () => {
      const event = await mongoService.createAnalyticsEvent(testEvent, testUserId, testData);
      expect(event.event).to.equal(testEvent);
      expect(event.userId).to.equal(testUserId);
      expect(event.data).to.deep.equal(testData);
    });
  });

  describe('Notifications', () => {
    const testUserId = 'test-user-123';
    const testType = 'TEST_NOTIFICATION';
    const testTitle = 'Test Title';
    const testMessage = 'Test Message';

    afterEach(async () => {
      await mongoService.delete('notifications', { userId: testUserId });
    });

    it('should create notification', async () => {
      const notification = await mongoService.createNotification(
        testUserId,
        testType,
        testTitle,
        testMessage,
      );

      expect(notification.userId).to.equal(testUserId);
      expect(notification.type).to.equal(testType);
      expect(notification.title).to.equal(testTitle);
      expect(notification.message).to.equal(testMessage);
      expect(notification.read).to.be.false;
    });

    it('should get unread notifications', async () => {
      await mongoService.createNotification(testUserId, testType, testTitle, testMessage);
      const notifications = await mongoService.getUnreadNotifications(testUserId);

      expect(notifications).to.be.an('array');
      expect(notifications).to.have.length(1);
      expect(notifications[0].read).to.be.false;
    });

    it('should mark notification as read', async () => {
      const notification = await mongoService.createNotification(
        testUserId,
        testType,
        testTitle,
        testMessage,
      );

      await mongoService.markNotificationAsRead(notification._id);
      const updated = await mongoService.findOne('notifications', { _id: notification._id });

      expect(updated.read).to.be.true;
    });
  });
});
