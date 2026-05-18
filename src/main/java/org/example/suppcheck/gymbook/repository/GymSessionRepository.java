package org.example.suppcheck.gymbook.repository;

import org.example.suppcheck.gymbook.model.GymSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface GymSessionRepository extends MongoRepository<GymSession, String> {

    /** Alle Sessions absteigend nach Datum. */
    List<GymSession> findAllByOrderByDateDesc();

    /** Die letzten N Sessions absteigend. */
    List<GymSession> findTop30ByOrderByDateDesc();
}
