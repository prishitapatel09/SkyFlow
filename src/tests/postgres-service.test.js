const { expect } = require('chai');
const db = require('../models/index');

describe('PostgreSQL Service', () => {
  before(async () => {
    // Ensure database connection
    await db.sequelize.authenticate();
  });

  after(async () => {
    // Close database connection
    await db.sequelize.close();
  });

  describe('Database Connection', () => {
    it('should connect to PostgreSQL successfully', async () => {
      try {
        await db.sequelize.authenticate();
        expect(true).to.be.true;
      } catch (error) {
        expect.fail('Database connection failed');
      }
    });

    it('should have required models', () => {
      expect(db).to.have.property('sequelize');
      expect(db.sequelize).to.have.property('authenticate');
    });
  });

  describe('Flight Model', () => {
    it('should have Flight model defined', () => {
      expect(db).to.have.property('Flight');
    });

    it('should have correct Flight model attributes', () => {
      const { Flight } = db;
      expect(Flight.rawAttributes).to.have.property('flightNumber');
      expect(Flight.rawAttributes).to.have.property('departureTime');
      expect(Flight.rawAttributes).to.have.property('arrivalTime');
      expect(Flight.rawAttributes).to.have.property('price');
    });
  });

  describe('City Model', () => {
    it('should have City model defined', () => {
      expect(db).to.have.property('City');
    });

    it('should have correct City model attributes', () => {
      const { City } = db;
      expect(City.rawAttributes).to.have.property('name');
    });
  });

  describe('Airport Model', () => {
    it('should have Airport model defined', () => {
      expect(db).to.have.property('Airport');
    });

    it('should have correct Airport model attributes', () => {
      const { Airport } = db;
      expect(Airport.rawAttributes).to.have.property('name');
      expect(Airport.rawAttributes).to.have.property('code');
    });
  });

  describe('Airplane Model', () => {
    it('should have Airplane model defined', () => {
      expect(db).to.have.property('Airplane');
    });

    it('should have correct Airplane model attributes', () => {
      const { Airplane } = db;
      expect(Airplane.rawAttributes).to.have.property('model');
      expect(Airplane.rawAttributes).to.have.property('capacity');
    });
  });
});
