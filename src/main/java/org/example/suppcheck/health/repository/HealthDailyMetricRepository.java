package org.example.suppcheck.health.repository;

import org.example.suppcheck.health.model.HealthDailyMetric;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface HealthDailyMetricRepository extends MongoRepository<HealthDailyMetric, String> {

    List<HealthDailyMetric> findByDateBetweenOrderByDateAsc(String from, String to);
}
