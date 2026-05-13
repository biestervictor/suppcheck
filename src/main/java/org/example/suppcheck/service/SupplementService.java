package org.example.suppcheck.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.example.suppcheck.dto.IngredientDto;
import org.example.suppcheck.dto.IngredientWithSources;
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
      int intervalDays = effectiveIntervalDays(supplement);
      for (Ingredient ing : supplement.getIngredients()) {
        if (ing.getName() == null || ing.getName().isBlank()) continue;
        sumMap.merge(ing.getName(), ing.getMg() / intervalDays, Double::sum);
        for (Ingredient subIng : ing.getSubIngredients()) {
          if (subIng.getName() == null || subIng.getName().isBlank()) continue;
          sumMap.merge(subIng.getName(), subIng.getMg() / intervalDays, Double::sum);
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
   * Daily intake summarized with per-supplement source information.
   * Each entry contains the total mg plus a list of contributing supplement names and their amounts.
   *
   * @param supplements  all supplements (active + inactive – filtering happens here)
   * @param isWorkoutDay true to include SPORT-type supplements
   * @return list of ingredients with source details, sorted by name
   */
  public List<IngredientWithSources> getSummedIngredientsWithSources(
      List<Supplement> supplements, boolean isWorkoutDay) {
    // ingredient name → (supplement name → contributed daily mg)
    Map<String, Map<String, Double>> sourceMap = new TreeMap<>();
    // supplement name → interval (only stored when > 1, for label generation)
    Map<String, Integer> supplementIntervals = new HashMap<>();

    for (Supplement supplement : supplements) {
      if (supplement.isInactive()
          || (!isWorkoutDay && SupplementType.SPORT.name().equals(supplement.getSupplementType()))) {
        continue;
      }
      int intervalDays = effectiveIntervalDays(supplement);
      if (intervalDays > 1) {
        supplementIntervals.put(supplement.getName(), intervalDays);
      }
      for (Ingredient ing : supplement.getIngredients()) {
        if (ing.getName() == null || ing.getName().isBlank()) continue;
        sourceMap
            .computeIfAbsent(ing.getName(), k -> new LinkedHashMap<>())
            .merge(supplement.getName(), ing.getMg() / intervalDays, Double::sum);
        for (Ingredient sub : ing.getSubIngredients()) {
          if (sub.getName() == null || sub.getName().isBlank()) continue;
          sourceMap
              .computeIfAbsent(sub.getName(), k -> new LinkedHashMap<>())
              .merge(supplement.getName(), sub.getMg() / intervalDays, Double::sum);
        }
      }
    }

    List<IngredientWithSources> result = new ArrayList<>();
    sourceMap.forEach((ingName, sources) -> {
      double total = sources.values().stream().mapToDouble(Double::doubleValue).sum();
      List<String> sourceLabels = sources.entrySet().stream()
          .map(e -> {
            String suppName = e.getKey();
            String intervalSuffix = supplementIntervals.containsKey(suppName)
                ? " (Alle " + supplementIntervals.get(suppName) + " Tage)"
                : "";
            return suppName + intervalSuffix + ": " + formatMg(e.getValue());
          })
          .toList();
      result.add(new IngredientWithSources(ingName, total, sourceLabels));
    });
    return result;
  }

  private String formatMg(double mg) {
    if (Math.abs(mg) >= 1000) {
      return String.format("%,.0f mg", mg).replace(",", ".");
    }
    return String.format("%.1f mg", mg).replace(".", ",");
  }

  /**
   * Returns the effective interval in days for a supplement.
   * Returns 1 for daily supplements; returns the configured interval (≥ 2) for non-daily ones.
   */
  private int effectiveIntervalDays(Supplement supplement) {
    if (supplement.isNonDaily() && supplement.getConsumptionIntervalDays() > 1) {
      return supplement.getConsumptionIntervalDays();
    }
    return 1;
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
    Double incomingOvp = supplement.getCurrentOvp();

    Optional<Supplement> existingOpt = supplement.getId() == null
        ? Optional.empty()
        : supplementRepository.findById(supplement.getId());

    if (existingOpt.isPresent()) {
      Supplement existing = existingOpt.get();

      // Ensure history list exists and is mutable
      ensureMutablePricesList(existing);


      double previousPrice = existing.getPrice();
      double previousOvp = existing.getOvp();

      // Copy non-price fields onto existing entity
      existing.setShop(supplement.getShop());
      existing.setName(supplement.getName());
      existing.setInactive(supplement.isInactive());
      existing.setPortionSize(supplement.getPortionSize());
      existing.setSupplementType(supplement.getSupplementType());
      existing.setIngredients(supplement.getIngredients());
      existing.setDiscount(supplement.getDiscount());
      existing.setMhdProdukt(supplement.isMhdProdukt());
      existing.setNonDaily(supplement.isNonDaily());
      existing.setConsumptionIntervalDays(supplement.getConsumptionIntervalDays());

      // Append a new combined entry when either price or OVP changed
      boolean priceChanged = incomingPrice != null && Double.compare(previousPrice, incomingPrice) != 0;
      boolean ovpChanged = incomingOvp != null && Double.compare(previousOvp, incomingOvp) != 0;

      if (priceChanged || ovpChanged) {
        PriceEntry entry = new PriceEntry();
        entry.setDate(LocalDate.now());
        entry.setPrice(incomingPrice != null ? incomingPrice : previousPrice);
        entry.setOvp(incomingOvp != null ? incomingOvp : previousOvp);
        existing.getPrices().add(entry);
      }


      supplementRepository.save(existing);
      return;
    }

    // New supplement
    ensureMutablePricesList(supplement);

    if (incomingPrice != null || incomingOvp != null) {
      PriceEntry entry = new PriceEntry();
      entry.setDate(LocalDate.now());
      entry.setPrice(incomingPrice != null ? incomingPrice : 0d);
      entry.setOvp(incomingOvp != null ? incomingOvp : 0d);
      supplement.getPrices().add(entry);
    }


    supplementRepository.save(supplement);
  }

  /**
   * Ensures the prices list on the supplement is mutable.
   */
  private void ensureMutablePricesList(Supplement supp) {
    if (supp.getPrices() == null) {
      supp.setPrices(new ArrayList<>());
    } else if (!(supp.getPrices() instanceof ArrayList)) {
      supp.setPrices(new ArrayList<>(supp.getPrices()));
    }
  }

  /**
   * Adjusts the stock of a supplement by the given delta (positive = increment, negative = decrement).
   * Stock cannot go below 0.
   *
   * @param id    the id of the supplement
   * @param delta the amount to add (may be negative)
   * @return the new stock value
   * @throws IllegalArgumentException when no supplement with the given id is found
   */
  public int adjustStock(String id, int delta) {
    Supplement supp = supplementRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Supplement mit ID " + id + " nicht gefunden"));
    int newStock = Math.max(0, supp.getStock() + delta);
    supp.setStock(newStock);
    supplementRepository.save(supp);
    return newStock;
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

  /**
   * Builds a template ingredient list from all active WHEY supplements.
   *
   * <p>The template contains the union of all ingredient names (and their
   * sub-ingredient names) found across existing WHEY supplements.  Amounts
   * (mg) are set to 0 so the user only has to fill in the quantities.</p>
   *
   * @return ordered list of ingredient templates; empty when no WHEY supplements exist
   */
  public List<IngredientDto> getWheyIngredientTemplate() {
    // Preserve first-seen insertion order; key = ingredient name
    Map<String, IngredientDto> topLevel = new LinkedHashMap<>();

    for (Supplement supp : supplementRepository.findAll()) {
      if (!SupplementType.WHEY.name().equals(supp.getSupplementType())) {
        continue;
      }
      if (supp.isInactive()) {
        continue;
      }

      for (Ingredient ing : supp.getIngredients()) {
        if (ing.getName() == null || ing.getName().isBlank()) {
          continue;
        }

        IngredientDto topDto = topLevel.computeIfAbsent(ing.getName(), name -> {
          IngredientDto dto = new IngredientDto();
          dto.setName(name);
          dto.setMg(0);
          return dto;
        });

        for (Ingredient sub : ing.getSubIngredients()) {
          if (sub.getName() == null || sub.getName().isBlank()) {
            continue;
          }
          boolean alreadyPresent = topDto.getSubIngredients().stream()
              .anyMatch(s -> sub.getName().equals(s.getName()));
          if (!alreadyPresent) {
            IngredientDto subDto = new IngredientDto();
            subDto.setName(sub.getName());
            subDto.setMg(0);
            topDto.getSubIngredients().add(subDto);
          }
        }
      }
    }

    return new ArrayList<>(topLevel.values());
  }
}