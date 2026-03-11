package org.example.suppcheck.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.PriceEntry;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.model.SupplementType;
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
  public List<Ingredient> getSummedIngredients(List<Supplement> supplements, boolean isWorkoutDay) {
    Map<String, Double> sumMap = new HashMap<>();
    for (Supplement supplement : supplements) {
      if (supplement.isInactive() ||
          (!isWorkoutDay && supplement.getSupplementType().equals(SupplementType.SPORT.name()))) {
        continue; // Nur aktive Supplements berücksichtigen und nur WORKOUT Supplements an Trainingstagen
      }
      for (Ingredient ing : supplement.getIngredients()) {

        sumMap.merge(ing.getName(), ing.getMg(), Double::sum);
        for (Ingredient subIng : ing.getSubIngredients()) {
          sumMap.merge(subIng.getName(), subIng.getMg(), Double::sum);
        }
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
   * @param id the name of the supplement to delete
   */
  public void deleteSupplementById(String id) {
    Supplement supplement = supplementRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Supplement mit ID " + id + " nicht gefunden"));

    supplementRepository.delete(supplement);

  }

  /**
   * Save a supplement.
   *
   * <p>Rules for price history:</p>
   * <ul>
   *   <li>On update, a new entry for today is appended only when the price changed.</li>
   *   <li>For new supplements, a single entry for today is created.</li>
   * </ul>
   *
   * @param supplement the Supplement to save
   */
  public void saveSupplement(Supplement supplement) {
    if (supplement == null) {
      return;
    }

    // price entered in UI/DTO (transient). If not provided, keep current historical price.
    Double incomingPrice = supplement.getCurrentPrice();

    Optional<Supplement> existingOpt = supplement.getId() == null
        ? Optional.empty()
        : supplementRepository.findById(supplement.getId());

    if (existingOpt.isPresent()) {
      Supplement existing = existingOpt.get();

      // Ensure history list exists and is mutable
      if (existing.getPrices() == null) {
        existing.setPrices(new ArrayList<>());
      } else if (!(existing.getPrices() instanceof ArrayList)) {
        existing.setPrices(new ArrayList<>(existing.getPrices()));
      }


      double previousPrice = existing.getPrice();

      // Copy non-price fields onto existing entity
      existing.setShop(supplement.getShop());
      existing.setName(supplement.getName());
      existing.setInactive(supplement.isInactive());
      existing.setPortionSize(supplement.getPortionSize());
      existing.setSupplementType(supplement.getSupplementType());
      existing.setIngredients(supplement.getIngredients());

      // Only append a new history row when the UI actually sent a price.
      // After legacy migration, previousPrice is the legacy value, so the new price will append.
      if (incomingPrice != null && Double.compare(previousPrice, incomingPrice) != 0) {
        PriceEntry entry = new PriceEntry();
        entry.setDate(LocalDate.now());
        entry.setPrice(incomingPrice);
        existing.getPrices().add(entry);
      }


      supplementRepository.save(existing);
      return;
    }

    // New supplement
    if (supplement.getPrices() == null) {
      supplement.setPrices(new ArrayList<>());
    } else if (!(supplement.getPrices() instanceof ArrayList)) {
      supplement.setPrices(new ArrayList<>(supplement.getPrices()));
    }

    if (incomingPrice != null) {
      PriceEntry entry = new PriceEntry();
      entry.setDate(LocalDate.now());
      entry.setPrice(incomingPrice);
      supplement.getPrices().add(entry);
    }


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