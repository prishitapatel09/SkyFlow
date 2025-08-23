const { CityService } = require('../services/index');
const errorResponse = require('../utils/error-response');

const cityService = new CityService();

const RabbitMQService = require('../services/rabbitmq-service');

const rabbitmq = new RabbitMQService();

const create = async (req, res) => {
  try {
    const city = await cityService.createCity(req.body);
    await rabbitmq.publish({
      type: 'CITY_CREATED',
      data: city,
      timestamp: new Date(),
    });
    return res.status(201).json({
      data: city,
      success: true,
      message: 'Successfully created a city',
      err: {},
    });
  } catch (error) {
    console.log(error);
    return res.status(500).json(
      errorResponse.create('Not able to create a city', error),
    );
  }
};
// DELETE. -> /city/:id
const destroy = async (req, res) => {
  try {
    const response = await cityService.deleteCity(req.params.id);
    return res.status(200).json({
      data: response,
      success: true,
      message: 'Successfully deleted a city',
      err: {},
    });
  } catch (error) {
    console.log(error);
    return res.status(500).json({
      data: {},
      success: false,
      message: 'Not able to delete the city',
      err: error,
    });
  }
};

// GET -> /city/:id
const get = async (req, res) => {
  try {
    const response = await cityService.getCity(req.params.id);
    return res.status(200).json({
      data: response,
      success: true,
      message: 'Successfully fetched a city',
      err: {},
    });
  } catch (error) {
    console.log(error);
    return res.status(500).json({
      data: {},
      success: false,
      message: 'Not able to get the city',
      err: error,
    });
  }
};

// Patch -> /city/:id -> req.body
const update = async (req, res) => {
  try {
    const response = await cityService.updateCity(req.params.id, req.body);
    return res.status(200).json({
      data: response,
      success: true,
      message: 'Successfully fetched a city',
      err: {},
    });
  } catch (error) {
    console.log(error);
    return res.status(500).json({
      data: {},
      success: false,
      message: 'Not able to update the city',
      err: error,
    });
  }
};
// getAll
const getAll = async (req, res) => {
  try {
    const citites = await cityService.getAllCities(req.query);
    return res.status(200).json({
      data: citites,
      success: true,
      message: 'Successfully fetched all cities',
      err: {},
    });
  } catch (error) {
    console.log(error);
    return res.status(500).json(
      errorResponse.create('Not able to fetch the cities', error),
    );
  }
};
module.exports = {
  create,
  destroy,
  get,
  update,
  getAll,
};
