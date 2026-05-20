package org.example.suppcheck.repository;

import org.example.suppcheck.model.Hersteller;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface HerstellerRepository extends MongoRepository<Hersteller, String> {

    List<Hersteller> findAllByOrderByNameAsc();

    Optional<Hersteller> findByName(String name);
}
