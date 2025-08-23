const { expect } = require('chai');
const RedisService = require('../services/redis-service');

describe('RedisService', () => {
  let redisService;

  before(async () => {
    redisService = new RedisService();
  });

  after(async () => {
    await redisService.client.quit();
  });

  it('should set and get value from Redis', async () => {
    const key = 'testKey';
    const value = { test: 'value' };

    await redisService.set(key, value);
    const result = await redisService.get(key);

    expect(result).to.deep.equal(value);
  });

  it('should delete value from Redis', async () => {
    const key = 'testKey2';
    const value = { test: 'value2' };

    await redisService.set(key, value);
    await redisService.delete(key);
    const result = await redisService.get(key);

    expect(result).to.be.null;
  });
});
