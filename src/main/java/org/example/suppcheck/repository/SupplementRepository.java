package org.example.suppcheck.repository;

import org.example.suppcheck.model.Supplement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * MongoDB Repository interface for the Supplement entity.
 */

@Repository
public interface SupplementRepository extends MongoRepository<Supplement, String> {
  /**
   * Finds a supplement by its name.
   *
   * @param name the name of the supplement to find
   * @return the Supplement object if found, otherwise null
   */
  Supplement findByName(String name);
}