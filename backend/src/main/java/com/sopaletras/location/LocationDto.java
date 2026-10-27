package com.sopaletras.location;

public record LocationDto(
        Long id,
        String city,
        String stateIso,
        String stateName,
        String countryIso,
        String countryName,
        String displayName) {

    public static LocationDto of(Location l) {
        return l == null ? null : new LocationDto(l.getId(), l.getCity(), l.getStateIso(), l.getStateName(),
                l.getCountryIso(), l.getCountryName(), l.displayName());
    }
}
