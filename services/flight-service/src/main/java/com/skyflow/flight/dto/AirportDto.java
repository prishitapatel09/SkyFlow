package com.skyflow.flight.dto;

public record AirportDto(Long id, String name, String code, String address, CityDto city) {
}
