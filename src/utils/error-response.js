const errorResponse = {
  /**
     * Creates a standardized error response object
     * @param {string} message - Error message
     * @param {Error} error - Error object
     * @param {number} statusCode - HTTP status code
     * @param {object} data - Additional error data
     * @returns {object} Standardized error response
     */
  create: (message, error, statusCode = 500, data = {}) => ({
    success: false,
    message,
    data,
    err: error?.message || 'Internal Server Error',
    timestamp: new Date().toISOString(),
    statusCode,
  }),
};

module.exports = errorResponse;
