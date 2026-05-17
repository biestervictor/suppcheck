package org.example.suppcheck.health.repository;

import org.example.suppcheck.health.model.HealthMetric;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HealthMetricRepository extends MongoRepository<HealthMetric, String> {

    List<HealthMetric> findByTypeAndDateBetweenOrderByDateAsc(String type, LocalDate from, LocalDate to);

    Optional<HealthMetric> findTopByTypeOrderByDateDesc(String type);

    List<HealthMetric> findByTypeOrderByDateAsc(String type);
}
