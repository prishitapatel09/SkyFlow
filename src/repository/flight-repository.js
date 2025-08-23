const {Flights, Airport} = require('../models/index');
const {Op} = require('sequelize');
class FlightRepository
{

    #createFilter(data)
    {
        let filter = {};
        if(data.arrivalAirportId)
        {
           
                filter.arrivalAirportId = data.arrivalAirportId ;
        }
        if(data.departureAirportId)
        {
            filter.departureAirportId = data.departureAirportId;
        }
        // if(data.minPrice && data.maxPrice){
        //     Object.assign(filter,{
        //         [Op.and] : [
        //             {price : {[Op.lte]: data.maxPrice}}
        //             ,
        //             {price:{[Op.gte]:data.minPrice}}
        //         ]
        //     })
        // }
        // if(data.minPrice){
        //     Object.assign(filter,{price : {[Op.gte]:data.minPrice}}); 
        // }
        // if(data.maxPrice)
        // {
        //     Object.assign(filter,{price : {[Op.lte]:data.maxPrice}}); 
        // }
        let priceFilter = [];
        if(data.minPrice)
        {
            priceFilter.push({price : {[Op.gte]:data.minPrice}});
        }
        if(data.maxPrice)
        {
            priceFilter.push({price : {[Op.lte]:data.maxPrice}});
        }
        return filter;
    }
    
    async createFlight(data)
    {
        try {
            const flight = await Flights.create(data);
            return flight ;
        } catch (error) {
            console.log("Something went wrong in the repository layer");
            throw {error};
        }
    }
    
    async getFlight(flightId)
    {
        try {
            const flight = await Flights.findByPk(flightId, {
                include: [
                    {
                        model: Airport,
                        as: 'departureAirport',
                        required: true
                    },
                    {
                        model: Airport,
                        as: 'arrivalAirport',
                        required: true
                    }
                ]
            });
            return flight;
        } catch (error) {
            console.log("Something went wrong in the repository layer");
            throw {error};
        }
    }
    async getAllFlight(filter) {
        try {
            const queryObject = {};
            
            if(filter.departureAirportId) {
                queryObject.departureAirportId = filter.departureAirportId;
            }
            
            if(filter.arrivalAirportId) {
                queryObject.arrivalAirportId = filter.arrivalAirportId;
            }
            
            if(filter.minPrice && filter.maxPrice) {
                queryObject.price = {
                    [Op.between]: [filter.minPrice, filter.maxPrice]
                };
            }
            
            const flights = await Flights.findAll({
                where: queryObject
            });
            
            return flights;
        } catch (error) {
            console.log("Something went wrong in the repository layer");
            throw {error};
        }
    }


async updateFlights(flightId,data)
{
    try {
            await Flights.update(data,{
                where :{
                    id: flightId,
                }
            });
    } catch (error) {
        console.log("something went wrong in repository layer");
        throw {error} ;
    }
}

}


module.exports = FlightRepository;