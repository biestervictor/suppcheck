package org.example.suppcheck.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.example.suppcheck.model.DailyIntakeSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for DailyIntakeSnapshot documents.
 */
public interface DailyIntakeSnapshotRepository extends MongoRepository<DailyIntakeSnapshot, String> {

  /** Returns all snapshots sorted by date ascending (for chart rendering). */
  List<DailyIntakeSnapshot> findAllByOrderByDateAsc();

  /** Checks whether a snapshot already exists for the given date (deduplication). */
  Optional<DailyIntakeSnapshot> findByDate(LocalDate date);
}
