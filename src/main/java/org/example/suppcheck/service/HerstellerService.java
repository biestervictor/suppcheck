package org.example.suppcheck.service;

import org.example.suppcheck.model.Hersteller;
import org.example.suppcheck.model.Shop;
import org.example.suppcheck.repository.HerstellerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class HerstellerService {

    private static final Logger log = LoggerFactory.getLogger(HerstellerService.class);

    private final HerstellerRepository herstellerRepository;

    public HerstellerService(HerstellerRepository herstellerRepository) {
        this.herstellerRepository = herstellerRepository;
    }

    /**
     * Seed the collection from the legacy {@link Shop} enum on first startup.
     * Runs only when the collection is empty so it never overwrites user additions.
     * Failures (e.g. MongoDB not yet reachable in test environments) are logged and ignored.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedFromShopEnum() {
        try {
            if (herstellerRepository.count() == 0) {
                Arrays.stream(Shop.values())
                        .map(s -> new Hersteller(s.name()))
                        .forEach(herstellerRepository::save);
                log.info("Hersteller collection seeded with {} entries from Shop enum.", Shop.values().length);
            }
        } catch (Exception e) {
            log.warn("Hersteller seed skipped – MongoDB not available: {}", e.getMessage());
        }
    }

    /** Returns all manufacturers sorted alphabetically by name. */
    public List<Hersteller> findAll() {
        return herstellerRepository.findAllByOrderByNameAsc();
    }

    /** Returns manufacturer names sorted alphabetically – used to populate form dropdowns. */
    public List<String> findAllNames() {
        return findAll().stream().map(Hersteller::getName).toList();
    }

    /**
     * Adds a new manufacturer.
     *
     * @param name the name to add (trimmed, must not be blank)
     * @throws IllegalArgumentException if the name is blank or already exists
     */
    public Hersteller add(String name) {
        String trimmed = (name == null ? "" : name.trim());
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Hersteller-Name darf nicht leer sein.");
        }
        if (herstellerRepository.findByName(trimmed).isPresent()) {
            throw new IllegalArgumentException("Hersteller '" + trimmed + "' existiert bereits.");
        }
        return herstellerRepository.save(new Hersteller(trimmed));
    }

    /**
     * Deletes a manufacturer by ID.
     *
     * @param id the MongoDB document ID
     */
    public void delete(String id) {
        herstellerRepository.deleteById(id);
    }
}
