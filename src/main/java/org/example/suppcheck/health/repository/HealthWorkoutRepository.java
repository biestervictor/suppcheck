package org.example.suppcheck.health.repository;

import org.example.suppcheck.health.model.HealthWorkout;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface HealthWorkoutRepository extends MongoRepository<HealthWorkout, String> {

    List<HealthWorkout> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);

    List<HealthWorkout> findTop10ByOrderByDateDesc();

    List<HealthWorkout> findAllByOrderByDateDesc();

    long countByDateBetween(LocalDate from, LocalDate to);
}
