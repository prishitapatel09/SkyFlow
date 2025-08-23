const { AirportService } = require('../services/index');
const errorResponse = require('../utils/error-response');

const airportService = new AirportService();

const create = async (req, res) => {
  try {
    const response = await airportService.create(req.body);
    return res.status(201).json({
      data: response,
      success: true,
      err: {},
      message: 'created a new airport',
    });
  } catch (error) {
    console.log(error);
    return res.status(500).json(
      errorResponse.create('cannot create a new airport', error),
    );
  }
};

module.exports = {
  create,
};
