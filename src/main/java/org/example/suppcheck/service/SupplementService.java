package org.example.suppcheck.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.repository.SupplementRepository;
import org.springframework.stereotype.Service;

/**
 * SupplementService is a service class that provides methods to
 * interact with the SupplementRepository.
 */
@Service
public class SupplementService {

  private final SupplementRepository supplementRepository;

  /**
   * Constructor for this service.
   *
   * @param supplementRepository the SupplementRepository instance
   */
  public SupplementService(SupplementRepository supplementRepository) {
    this.supplementRepository = supplementRepository;
  }

  /**
   * Daily intake summerized.
   *
   * @param supplements the list of supplements to sum up
   * @return a list of summed ingredients
   */
  public List<Ingredient> getSummedIngredients(List<Supplement> supplements) {
    Map<String, Double> sumMap = new HashMap<>();
    for (Supplement supplement : supplements) {
      if (supplement.isInactive()) {
        continue; // Nur aktive Supplements berücksichtigen
      }
      for (Ingredient ing : supplement.getIngredients()) {
        sumMap.merge(ing.getName(), ing.getMg(), Double::sum);
      }
    }
    List<Ingredient> result = new ArrayList<>();
    sumMap.forEach((name, mg) -> {
      Ingredient ing = new Ingredient();
      ing.setName(name);
      ing.setMg(mg);
      result.add(ing);
    });
    return result;
  }

  /**
   * Delete a supplement.
   *
   * @param name the name of the supplement to delete
   */
  public void deleteSupplementByName(String name) {
    Supplement supplement = supplementRepository.findByName(name);
    if (supplement != null) {
      supplementRepository.delete(supplement);
    }
  }

  /**
   * Save a supplement.
   *
   * @param supplement the Supplement to save
   */
  public void saveSupplement(Supplement supplement) {
    supplementRepository.save(supplement);
  }

  /**
   * Get all Supplements.
   *
   * @return a list of all Supplements
   */
  public List<Supplement> getAllSupplements() {
    return supplementRepository.findAll();
  }

  /**
   * Get a supplement by ID.
   *
   * @param id the ID of the supplement to retrieve
   * @return an Optional containing the Supplement if found, or empty if not found
   */
  public Optional<Supplement> getSupplementById(String id) {
    return supplementRepository.findById(id);
  }
}