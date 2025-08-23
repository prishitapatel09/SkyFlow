const { FlightService } = require('../services/index');
const { ClientErrorCodes } = require('../utils/error-codes');
const errorResponse = require('../utils/error-response');

const flightService = new FlightService();

const create = async (req, res) => {
  try {
    const flightRequestData = {
      flightNumber: req.body.flightNumber,
      airplaneId: req.body.airplaneId,
      departureAirportId: req.body.departureAirportId,
      arrivalAirportId: req.body.arrivalAirportId,
      arrivalTime: req.body.arrivalTime,
      departureTime: req.body.departureTime,
      price: req.body.price,
    };
    const flight = await flightService.createFlight(flightRequestData);
    return res.status(ClientErrorCodes.CREATED).json({
      data: flight,
      success: true,
      err: {},
      message: 'Succesfully created a Flight',
    });
  } catch (error) {
    console.log(error);
    return res.status(500).json(
      errorResponse.create('Not able to create the Flight', error),
    );
  }
};
const getAll = async (req, res) => {
  try {
    const response = await flightService.getAllFlightData(req.query);
    return res.status(200).json({
      data: response,
      success: true,
      message: 'Successfully fetched all flights',
      err: {},
    });
  } catch (error) {
    console.log(error);
    return res.status(500).json(
      errorResponse.create('Not able to fetch the Flights', error),
    );
  }
};

const get = async (req, res) => {
  try {
    const response = await flightService.getFlight(req.params.id);
    return res.status(200).json({
      data: response,
      success: true,
      message: 'Successfully fetched the flight',
      err: {},
    });
  } catch (error) {
    console.log(error);
    return res.status(500).json(
      errorResponse.create('Not able to fetch the Flight', error),
    );
  }
};

const update = async (req, res) => {
  try {
    const response = await flightService.updateFlight(req.params.id, req.body);
    return res.status(201).json({
      data: response,
      success: true,
      err: {},
      message: 'Succesfully updated the Flight',
    });
  } catch (error) {
    console.log(error);
    return res.status(500).json(
      errorResponse.create('Not able to update the Flight', error),
    );
  }
};

module.exports = {
  create, getAll, get, update,
};
