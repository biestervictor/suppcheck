package org.example.suppcheck.repository;

import java.util.List;
import org.example.suppcheck.model.CostSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * MongoDB Repository für {@link CostSnapshot}.
 */
@Repository
public interface CostSnapshotRepository extends MongoRepository<CostSnapshot, String> {

  /**
   * Alle Snapshots eines Monats (Format YYYY-MM).
   */
  List<CostSnapshot> findByMonthOrderByDateAsc(String month);

  /**
   * Alle Snapshots chronologisch sortiert.
   */
  List<CostSnapshot> findAllByOrderByDateAsc();
}

