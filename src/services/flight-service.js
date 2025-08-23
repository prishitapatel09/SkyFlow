const { FlightRepository, AirplaneRepository } = require('../repository/index');
const { compareTime } = require('../utils/helper');
const RedisService = require('./redis-service');

class FlightService {
  constructor() {
    this.airplaneRepository = new AirplaneRepository();
    this.flightRepository = new FlightRepository();
    this.redisService = new RedisService();
  }

  async createFlight(data) {
    try {
      if (!compareTime(data.arrivalTime, data.departureTime)) {
        throw new Error('Arrival time cannot be less than departure time');
      }
      const airplane = await this.airplaneRepository.getAirplane(data.airplaneId);
      const flight = await this.flightRepository.createFlight({
        ...data, totalSeats: airplane.capacity,
      });

      // Invalidate the all flights cache since we added a new flight
      await this.redisService.delete('all_flights');

      return flight;
    } catch (error) {
      console.log('Something went wrong in the Service layer of Flight Service');
      throw error;
    }
  }

  async getAllFlightData(filter) {
    try {
      // Generate unique cache key based on filter
      const cacheKey = `flights_${JSON.stringify(filter)}`;

      // Check cache first
      const cachedData = await this.redisService.get(cacheKey);
      if (cachedData) {
        return cachedData;
      }

      // If not in cache, fetch from database
      const response = await this.flightRepository.getAllFlight(filter);

      // Cache the data with 1 hour expiration
      await this.redisService.set(cacheKey, response, 3600);

      return response;
    } catch (error) {
      console.log('Something went wrong in the Service layer of Flight Service');
      throw error;
    }
  }

  async getFlight(flightId) {
    try {
      const cacheKey = `flight_${flightId}`;

      // Check cache first
      const cachedData = await this.redisService.get(cacheKey);
      if (cachedData) {
        return cachedData;
      }

      // If not in cache, fetch from database
      const flight = await this.flightRepository.getFlight(flightId);

      // Cache the data with 30 minutes expiration
      await this.redisService.set(cacheKey, flight, 1800);

      return flight;
    } catch (error) {
      console.log('Something went wrong in the Service layer of Flight Service');
      throw error;
    }
  }

  async updateFlight(flightId, data) {
    try {
      const response = await this.flightRepository.updateFlights(flightId, data);

      // Invalidate cache for this flight
      await this.redisService.delete(`flight_${flightId}`);

      // Also invalidate the all flights cache since the list might be affected
      await this.redisService.delete('all_flights');

      return response;
    } catch (error) {
      console.log('Something went wrong in the Service layer of Flight Service');
      throw error;
    }
  }
}
/**
 * flightNumber

 * departureAirportId
 * arrivalAirportId
 * arrivalTime
 * departureTime
 * price
 * totalSeats ->fetch from airplane
 */
module.exports = FlightService;
