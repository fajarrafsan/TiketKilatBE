package com.projekan.tiket_pesawat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.projekan.tiket_pesawat.models.KetersediaanPenerbangan;
import com.projekan.tiket_pesawat.models.Penerbangan;
import com.projekan.tiket_pesawat.models.StatusPenerbangan;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=never"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PenerbanganRepositoryTest {

    private static final LocalDate FLIGHT_DATE = LocalDate.of(2037, 4, 12);

    @Autowired
    private PenerbanganRepository repository;

    private String fixturePrefix;
    private String departureCity;
    private String destinationCity;
    private String airline;
    private Penerbangan available;
    private Penerbangan unavailable;

    @BeforeEach
    void createIsolatedFixtures() {
        // Every test rolls back; unique names keep existing local data out of filtered assertions.
        fixturePrefix = "RepositoryTest" + UUID.randomUUID().toString().replace("-", "");
        departureCity = fixturePrefix + " JaKarTa";
        destinationCity = fixturePrefix + " BaLi";
        airline = fixturePrefix + " GaRuDa";
        available = saveFlight(departureCity, destinationCity, airline,
                FLIGHT_DATE.atTime(10, 30), KetersediaanPenerbangan.TERSEDIA);
        unavailable = saveFlight(departureCity, destinationCity, airline,
                FLIGHT_DATE.atTime(10, 30), KetersediaanPenerbangan.TIDAK_TERSEDIA);
    }

    @Test
    void nullFiltersReturnAvailableFlightsWithoutNullableSqlParameterErrors() {
        List<Penerbangan> flights = repository.findFiltered(null, null, null, null);

        assertThat(flights).extracting(Penerbangan::getId)
                .contains(available.getId())
                .doesNotContain(unavailable.getId());
        assertThat(flights).allSatisfy(flight -> assertThat(flight.getKetersediaanPenerbangan())
                .isEqualTo(KetersediaanPenerbangan.TERSEDIA));
    }

    @Test
    void blankFiltersAreOmittedAndUnavailableFlightsStayExcluded() {
        List<Penerbangan> flights = repository.findFiltered(" ", "\t", null, "  \n  ");

        assertThat(flights).extracting(Penerbangan::getId)
                .contains(available.getId())
                .doesNotContain(unavailable.getId());
        assertThat(flights).allSatisfy(flight -> assertThat(flight.getKetersediaanPenerbangan())
                .isEqualTo(KetersediaanPenerbangan.TERSEDIA));
    }

    @Test
    void departureFilterMatchesCaseInsensitivelyWithOtherFiltersAbsent() {
        saveFlight(fixturePrefix + " Surabaya", destinationCity, airline,
                FLIGHT_DATE.atTime(10, 30), KetersediaanPenerbangan.TERSEDIA);

        List<Penerbangan> flights = repository.findFiltered(
                paddedUpperCase(fixturePrefix + " Jak"), null, null, null);

        assertThat(flights).extracting(Penerbangan::getId).containsExactly(available.getId());
    }

    @Test
    void destinationFilterMatchesCaseInsensitivelyWithOtherFiltersAbsent() {
        saveFlight(departureCity, fixturePrefix + " Medan", airline,
                FLIGHT_DATE.atTime(10, 30), KetersediaanPenerbangan.TERSEDIA);

        List<Penerbangan> flights = repository.findFiltered(
                null, paddedUpperCase(fixturePrefix + " Bal"), null, null);

        assertThat(flights).extracting(Penerbangan::getId).containsExactly(available.getId());
    }

    @Test
    void airlineFilterMatchesCaseInsensitivelyWithOtherFiltersAbsent() {
        saveFlight(departureCity, destinationCity, fixturePrefix + " Citilink",
                FLIGHT_DATE.atTime(10, 30), KetersediaanPenerbangan.TERSEDIA);

        List<Penerbangan> flights = repository.findFiltered(
                null, null, null, paddedUpperCase(fixturePrefix + " Gar"));

        assertThat(flights).extracting(Penerbangan::getId).containsExactly(available.getId());
    }

    @Test
    void combinedFiltersRequireEveryProvidedCriterion() {
        saveFlight(fixturePrefix + " Surabaya", destinationCity, airline,
                FLIGHT_DATE.atTime(10, 30), KetersediaanPenerbangan.TERSEDIA);
        saveFlight(departureCity, fixturePrefix + " Medan", airline,
                FLIGHT_DATE.atTime(10, 30), KetersediaanPenerbangan.TERSEDIA);
        saveFlight(departureCity, destinationCity, fixturePrefix + " Citilink",
                FLIGHT_DATE.atTime(10, 30), KetersediaanPenerbangan.TERSEDIA);
        saveFlight(departureCity, destinationCity, airline,
                FLIGHT_DATE.plusDays(1).atTime(10, 30), KetersediaanPenerbangan.TERSEDIA);

        List<Penerbangan> flights = repository.findFiltered(
                paddedUpperCase(departureCity), paddedUpperCase(destinationCity),
                FLIGHT_DATE, paddedUpperCase(airline));

        assertThat(flights).extracting(Penerbangan::getId).containsExactly(available.getId());
    }

    @Test
    void dateOnlyFilterIncludesMidnightAndExcludesTheNextDay() {
        Penerbangan beforeDay = saveFlight(departureCity, destinationCity, airline,
                FLIGHT_DATE.atStartOfDay().minusSeconds(1), KetersediaanPenerbangan.TERSEDIA);
        Penerbangan atStart = saveFlight(departureCity, destinationCity, airline,
                FLIGHT_DATE.atStartOfDay(), KetersediaanPenerbangan.TERSEDIA);
        Penerbangan atEnd = saveFlight(departureCity, destinationCity, airline,
                FLIGHT_DATE.plusDays(1).atStartOfDay().minusSeconds(1), KetersediaanPenerbangan.TERSEDIA);
        Penerbangan nextDay = saveFlight(departureCity, destinationCity, airline,
                FLIGHT_DATE.plusDays(1).atStartOfDay(), KetersediaanPenerbangan.TERSEDIA);

        List<Penerbangan> flights = repository.findFiltered(null, null, FLIGHT_DATE, null);

        assertThat(flights).extracting(Penerbangan::getId)
                .contains(available.getId(), atStart.getId(), atEnd.getId())
                .doesNotContain(beforeDay.getId(), nextDay.getId(), unavailable.getId());
        assertThat(flights).allSatisfy(flight -> {
            assertThat(flight.getWaktuKeberangkatan().toLocalDate()).isEqualTo(FLIGHT_DATE);
            assertThat(flight.getKetersediaanPenerbangan()).isEqualTo(KetersediaanPenerbangan.TERSEDIA);
        });
    }

    private Penerbangan saveFlight(String from, String to, String carrier,
            LocalDateTime departure, KetersediaanPenerbangan availability) {
        return repository.saveAndFlush(Penerbangan.builder()
                .kotaKeberangkatan(from)
                .kotaTujuan(to)
                .maskapai(carrier)
                .waktuKeberangkatan(departure)
                .waktuKedatangan(departure.plusHours(2))
                .hargaTiket(new BigDecimal("750000.00"))
                .kursi(20)
                .ketersediaanPenerbangan(availability)
                .statusPenerbangan(StatusPenerbangan.ON_TIME)
                .build());
    }

    private String paddedUpperCase(String value) {
        return "  " + value.toUpperCase(Locale.ROOT) + "  ";
    }
}
