package org.example.suppcheck.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.example.suppcheck.dto.IngredientDto;
import org.example.suppcheck.dto.IngredientWithSources;
import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.IngredientHistoryEntry;
import org.example.suppcheck.model.PriceEntry;
import org.example.suppcheck.model.StockBatch;
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
  private final IngredientHistoryService ingredientHistoryService;

  /**
   * Constructor for this service.
   *
   * @param supplementRepository the SupplementRepository instance
   * @param ingredientHistoryService the IngredientHistoryService instance
   */
  public SupplementService(SupplementRepository supplementRepository,
                           IngredientHistoryService ingredientHistoryService) {
    this.supplementRepository = supplementRepository;
    this.ingredientHistoryService = ingredientHistoryService;
  }

  /**
   * Daily intake summerized.
   *
   * @param supplements the list of supplements to sum up
   * @return a list of summed ingredients
   */
  public List<Ingredient> getSummedIngredients(List<Supplement> supplements, boolean isWorkoutDay) {
    Map<String, Double> sumMap = new HashMap<>();
    Map<String, String> displayNames = new HashMap<>();
    for (Supplement supplement : supplements) {
      if (supplement.isInactive() ||
          (!isWorkoutDay && supplement.getSupplementType().equals(SupplementType.SPORT.name()))) {
        continue; // Nur aktive Supplements berücksichtigen und nur WORKOUT Supplements an Trainingstagen
      }
      int intervalDays = effectiveIntervalDays(supplement);
      for (Ingredient ing : supplement.getIngredients()) {
        if (ing.getName() == null || ing.getName().isBlank()) continue;
        String key = normalizeIngName(ing.getName());
        displayNames.putIfAbsent(key, ing.getName());
        sumMap.merge(key, ing.getMg() / intervalDays, Double::sum);
        for (Ingredient subIng : ing.getSubIngredients()) {
          if (subIng.getName() == null || subIng.getName().isBlank()) continue;
          String subKey = normalizeIngName(subIng.getName());
          displayNames.putIfAbsent(subKey, subIng.getName());
          sumMap.merge(subKey, subIng.getMg() / intervalDays, Double::sum);
        }
      }
    }
    List<Ingredient> result = new ArrayList<>();
    sumMap.forEach((key, mg) -> {
      Ingredient ing = new Ingredient();
      ing.setName(displayNames.getOrDefault(key, key));
      ing.setMg(mg);
      result.add(ing);
    });
    return result;
  }

  /**
   * Daily intake summarized with per-supplement source information.
   * Each entry contains the total mg plus a list of contributing supplement names and their amounts.
   *
   * <p>Ingredient names are normalised before grouping so that variants like
   * {@code L-Citrullin Malat} and {@code L-Citrullin-Malat}, or names with
   * brand suffixes in parentheses, are merged into one entry.</p>
   *
   * @param supplements  all supplements (active + inactive – filtering happens here)
   * @param isWorkoutDay true to include SPORT-type supplements
   * @return list of ingredients with source details, sorted by name
   */
  public List<IngredientWithSources> getSummedIngredientsWithSources(
      List<Supplement> supplements, boolean isWorkoutDay) {
    // normalised ingredient name → (supplement name → contributed daily mg)
    Map<String, Map<String, Double>> sourceMap = new TreeMap<>();
    // normalised name → first-seen original display name
    Map<String, String> displayNames = new HashMap<>();
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
        String key = normalizeIngName(ing.getName());
        displayNames.putIfAbsent(key, ing.getName());
        sourceMap
            .computeIfAbsent(key, k -> new LinkedHashMap<>())
            .merge(supplement.getName(), ing.getMg() / intervalDays, Double::sum);
        for (Ingredient sub : ing.getSubIngredients()) {
          if (sub.getName() == null || sub.getName().isBlank()) continue;
          String subKey = normalizeIngName(sub.getName());
          displayNames.putIfAbsent(subKey, sub.getName());
          sourceMap
              .computeIfAbsent(subKey, k -> new LinkedHashMap<>())
              .merge(supplement.getName(), sub.getMg() / intervalDays, Double::sum);
        }
      }
    }

    List<IngredientWithSources> result = new ArrayList<>();
    sourceMap.forEach((normalizedKey, sources) -> {
      String displayName = displayNames.getOrDefault(normalizedKey, normalizedKey);
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
      result.add(new IngredientWithSources(displayName, total, sourceLabels));
    });
    return result;
  }

  /**
   * Normalises an ingredient name for grouping purposes:
   * strips parenthetical content, replaces hyphens with spaces,
   * lowercases and collapses whitespace.
   */
  private static String normalizeIngName(String name) {
    if (name == null) return "";
    return name
        .replaceAll("\\s*\\([^)]*\\)", "")  // strip (HydroPrime®) etc.
        .replace("-", " ")                  // L-Citrullin → L Citrullin
        .toLowerCase(java.util.Locale.ROOT)
        .trim()
        .replaceAll("\\s+", " ");
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

      // Snapshot old ingredients before overwriting
      List<Ingredient> oldIngredients = new ArrayList<>(
          existing.getIngredients() != null ? existing.getIngredients() : List.of());

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

      // Bestände und Flavors aktualisieren (leere Liste = alle Batches löschen)
      List<StockBatch> incomingBatches = supplement.getStockBatches();
      if (incomingBatches != null) {
        existing.setStockBatches(new ArrayList<>(incomingBatches));
        // Lagerbestand = Summe aller verbleibenden Einheiten (0 bei leerer Liste)
        int totalStock = incomingBatches.stream()
            .mapToInt(b -> b.getRemaining() != null ? b.getRemaining() : b.getQuantity())
            .sum();
        existing.setStock(totalStock);
        // Flavors aus Batches ableiten (leer bei leerer Batch-Liste)
        List<String> derivedFlavors = incomingBatches.stream()
            .map(StockBatch::getFlavor)
            .filter(f -> f != null && !f.isBlank())
            .distinct()
            .sorted()
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        existing.setFlavors(derivedFlavors);
      }

      // Record ingredient history if anything changed
      ingredientHistoryService.buildEntry(oldIngredients, supplement.getIngredients())
          .ifPresent(entry -> {
            ensureMutableHistoryList(existing);
            existing.getIngredientHistory().add(entry);
          });

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

      // Uniqueness: nur ein Batch (global) darf inBenutzung=true tragen
      boolean hasInBenutzungBatch = existing.getStockBatches() != null &&
          existing.getStockBatches().stream().anyMatch(StockBatch::isInBenutzung);
      if (hasInBenutzungBatch) {
        clearInBenutzungBatchesExcept(existing.getId());
      }

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

    // Uniqueness: nur ein Batch (global) darf inBenutzung=true tragen
    boolean hasInBenutzungBatch = supplement.getStockBatches() != null &&
        supplement.getStockBatches().stream().anyMatch(StockBatch::isInBenutzung);
    if (hasInBenutzungBatch) {
      clearInBenutzungBatchesExcept(supplement.getId());
    }
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
   * Clears the {@code inBenutzung} flag on all batches of all supplements except the one
   * with the given id. Called after saving a supplement whose batch has {@code inBenutzung=true}.
   */
  private void clearInBenutzungBatchesExcept(String excludeId) {
    supplementRepository.findByStockBatchesInBenutzungIsTrue().forEach(other -> {
      if (!other.getId().equals(excludeId)) {
        other.getStockBatches().forEach(b -> b.setInBenutzung(false));
        supplementRepository.save(other);
      }
    });
  }

  /**
   * Ensures the ingredient history list on the supplement is mutable.
   */
  private void ensureMutableHistoryList(Supplement supp) {
    if (supp.getIngredientHistory() == null) {
      supp.setIngredientHistory(new ArrayList<>());
    } else if (!(supp.getIngredientHistory() instanceof ArrayList)) {
      supp.setIngredientHistory(new ArrayList<>(supp.getIngredientHistory()));
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
   * Fügt einen Restock-Batch hinzu und erhöht den Lagerbestand um dessen Menge.
   *
   * @param id          die Supplement-ID
   * @param batch       der hinzuzufügende Batch (Flavor, MHD, Datum, Menge)
   * @param inBenutzung wenn true: globale Uniqueness erzwingen (alle anderen Batches werden auf false gesetzt)
   * @return neuer Gesamtbestand
   * @throws IllegalArgumentException wenn kein Supplement mit der ID gefunden wurde
   */
  public int addStockBatch(String id, StockBatch batch, boolean inBenutzung) {
    Supplement supp = supplementRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Supplement mit ID " + id + " nicht gefunden"));
    if (supp.getStockBatches() == null) supp.setStockBatches(new java.util.ArrayList<>());
    supp.getStockBatches().add(batch);
    supp.setStock(supp.getStock() + batch.getQuantity());
    supplementRepository.save(supp);
    if (inBenutzung) {
      clearInBenutzungBatchesExcept(supp.getId());
    }
    return supp.getStock();
  }

  /**
   * Fügt einen Restock-Batch hinzu (ohne inBenutzung-Logik).
   *
   * @param id    die Supplement-ID
   * @param batch der hinzuzufügende Batch (Flavor, MHD, Datum, Menge)
   * @return neuer Gesamtbestand
   * @throws IllegalArgumentException wenn kein Supplement mit der ID gefunden wurde
   */
  public int addStockBatch(String id, StockBatch batch) {
    return addStockBatch(id, batch, false);
  }

  /**
   * Entnimmt eine Menge aus einem bestimmten Batch (Flavor + MHD) und reduziert
   * sowohl {@code remaining} des Batches als auch den Gesamt-Lagerbestand.
   *
   * <p>Wird kein passender Batch gefunden (z. B. Legacy-Daten ohne Tracking),
   * wird der Lagerbestand trotzdem reduziert.</p>
   *
   * @param id            die Supplement-ID
   * @param flavor        Flavor/Geschmacksrichtung (null oder leer = kein Flavor)
   * @param expiryDateStr MHD als ISO-String "yyyy-MM-dd" (null oder leer = kein MHD)
   * @param qty           zu entnehmende Menge (min. 1)
   * @return Map mit "stock" (neuer Gesamtbestand) und "wasInBenutzung" (boolean)
   * @throws IllegalArgumentException wenn kein Supplement mit der ID gefunden wurde
   */
  public Map<String, Object> consumeFromBatch(String id, String flavor, String expiryDateStr, int qty) {
    Supplement supp = supplementRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Supplement mit ID " + id + " nicht gefunden"));

    LocalDate expiryDate = (expiryDateStr != null && !expiryDateStr.isBlank())
        ? LocalDate.parse(expiryDateStr) : null;
    String normalizedFlavor = (flavor != null && !flavor.isBlank()) ? flavor : null;

    boolean wasInBenutzung = false;
    if (supp.getStockBatches() != null) {
      for (StockBatch batch : supp.getStockBatches()) {
        boolean flavorMatch = Objects.equals(batch.getFlavor(), normalizedFlavor);
        boolean expiryMatch = Objects.equals(batch.getExpiryDate(), expiryDate);
        if (flavorMatch && expiryMatch) {
          int effective = batch.getRemaining() != null ? batch.getRemaining() : batch.getQuantity();
          batch.setRemaining(Math.max(0, effective - qty));
          wasInBenutzung = batch.isInBenutzung();
          break;
        }
      }
    }

    int newStock = Math.max(0, supp.getStock() - qty);
    supp.setStock(newStock);
    supplementRepository.save(supp);

    Map<String, Object> result = new HashMap<>();
    result.put("stock", newStock);
    result.put("wasInBenutzung", wasInBenutzung);
    return result;
  }

  /**
   * Setzt den {@code inBenutzung}-Flag auf dem Batch, der durch Flavor + MHD identifiziert wird,
   * und löscht das Flag von allen anderen Batches (global).
   *
   * @param id            die Supplement-ID
   * @param flavor        Flavor/Geschmacksrichtung (null oder leer = kein Flavor)
   * @param expiryDateStr MHD als ISO-String "yyyy-MM-dd" (null oder leer = kein MHD)
   * @throws IllegalArgumentException wenn das Supplement nicht gefunden wurde oder kein passender Batch existiert
   */
  public void setInBenutzungBatch(String id, String flavor, String expiryDateStr) {
    Supplement supp = supplementRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Supplement mit ID " + id + " nicht gefunden"));

    LocalDate expiryDate = (expiryDateStr != null && !expiryDateStr.isBlank())
        ? LocalDate.parse(expiryDateStr) : null;
    String normalizedFlavor = (flavor != null && !flavor.isBlank()) ? flavor : null;

    boolean found = false;
    if (supp.getStockBatches() != null) {
      for (StockBatch batch : supp.getStockBatches()) {
        boolean flavorMatch = Objects.equals(batch.getFlavor(), normalizedFlavor);
        boolean expiryMatch = Objects.equals(batch.getExpiryDate(), expiryDate);
        if (flavorMatch && expiryMatch) {
          // Alle Batches dieses Supplements erst zurücksetzen, dann diesen setzen
          supp.getStockBatches().forEach(b -> b.setInBenutzung(false));
          batch.setInBenutzung(true);
          found = true;
          break;
        }
      }
    }
    if (!found) {
      throw new IllegalArgumentException("Kein passender Batch gefunden (Flavor=" + normalizedFlavor + ", MHD=" + expiryDate + ")");
    }
    supplementRepository.save(supp);
    // Globale Uniqueness: alle anderen Supplements zurücksetzen
    clearInBenutzungBatchesExcept(supp.getId());
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