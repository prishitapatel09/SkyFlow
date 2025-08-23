const { expect } = require('chai');
const RabbitMQService = require('../services/rabbitmq-service');

describe('RabbitMQService', () => {
  let rabbitmqService;

  before(async () => {
    rabbitmqService = new RabbitMQService();
    await rabbitmqService.connect();
  });

  after(async () => {
    await rabbitmqService.close();
  });

  it('should publish and consume messages', (done) => {
    const testMessage = { type: 'TEST', data: 'test message' };

    rabbitmqService.consume((message) => {
      expect(message).to.deep.equal(testMessage);
      done();
    });

    setTimeout(async () => {
      await rabbitmqService.publish(testMessage);
    }, 500);
  }).timeout(2000);
});
