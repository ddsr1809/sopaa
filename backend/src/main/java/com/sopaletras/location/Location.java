package com.sopaletras.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una ciudad del catálogo. El estado y el país se derivan de ella. */
@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "geoname_id")
    private Long geonameId;

    @Column(name = "country_iso", nullable = false)
    private String countryIso;

    @Column(name = "country_name", nullable = false)
    private String countryName;

    @Column(name = "state_iso", nullable = false)
    private String stateIso;

    @Column(name = "state_name", nullable = false)
    private String stateName;

    @Column(nullable = false)
    private String city;

    @Column(name = "search_key", nullable = false)
    private String searchKey;

    protected Location() {
    }

    public Long getId() {
        return id;
    }

    public Long getGeonameId() {
        return geonameId;
    }

    public String getCountryIso() {
        return countryIso;
    }

    public String getCountryName() {
        return countryName;
    }

    public String getStateIso() {
        return stateIso;
    }

    public String getStateName() {
        return stateName;
    }

    public String getCity() {
        return city;
    }

    public String displayName() {
        return city + ", " + stateName + ", " + countryName;
    }
}
