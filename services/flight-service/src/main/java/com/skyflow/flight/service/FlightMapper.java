package com.skyflow.flight.service;

import com.skyflow.flight.domain.Airplane;
import com.skyflow.flight.domain.Airport;
import com.skyflow.flight.domain.City;
import com.skyflow.flight.domain.Flight;
import com.skyflow.flight.dto.AirplaneDto;
import com.skyflow.flight.dto.AirportDto;
import com.skyflow.flight.dto.CityDto;
import com.skyflow.flight.dto.FlightDto;

/** Entity to DTO conversion. Must be called inside the transaction that loaded the entity. */
public final class FlightMapper {

    private FlightMapper() {
    }

    public static CityDto toDto(City city) {
        return city == null ? null : new CityDto(city.getId(), city.getName(), city.getCountryCode());
    }

    public static AirportDto toDto(Airport airport) {
        if (airport == null) {
            return null;
        }
        return new AirportDto(airport.getId(), airport.getName(), airport.getCode(),
                airport.getAddress(), toDto(airport.getCity()));
    }

    public static AirplaneDto toDto(Airplane airplane) {
        return airplane == null ? null
                : new AirplaneDto(airplane.getId(), airplane.getModelNumber(), airplane.getCapacity());
    }

    public static FlightDto toDto(Flight flight) {
        if (flight == null) {
            return null;
        }
        return new FlightDto(
                flight.getId(),
                flight.getFlightNumber(),
                toDto(flight.getDepartureAirport()),
                toDto(flight.getArrivalAirport()),
                toDto(flight.getAirplane()),
                flight.getDepartureTime(),
                flight.getArrivalTime(),
                FlightDto.minutesBetween(flight.getDepartureTime(), flight.getArrivalTime()),
                flight.getPrice(),
                flight.getBoardingGate(),
                flight.getTotalSeats(),
                flight.getAvailableSeats(),
                flight.getStatus());
    }
}
