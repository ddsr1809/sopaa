package com.sopaletras.location;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepository extends JpaRepository<Location, Long> {

    @Query("""
            select l from Location l
            where l.searchKey like concat('%', :q, '%')
              and (:country is null or l.countryIso = :country)
            order by case when l.searchKey like concat(:q, '%') then 0 else 1 end, l.city
            """)
    List<Location> search(@Param("q") String q, @Param("country") String country, Pageable pageable);

    Optional<Location> findFirstByStateIso(String stateIso);

    Optional<Location> findFirstByCountryIso(String countryIso);
}
