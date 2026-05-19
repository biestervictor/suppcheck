package org.example.suppcheck.mapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.example.suppcheck.dto.IngredientDto;
import org.example.suppcheck.dto.StockBatchDto;
import org.example.suppcheck.dto.SupplementSaveDto;
import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.StockBatch;
import org.example.suppcheck.model.Supplement;

public final class SupplementMapper {

  private SupplementMapper() {}

  public static Supplement toEntity(SupplementSaveDto dto) {
    if (dto == null) {
      return null;
    }
    Supplement supp = new Supplement();
    supp.setId(trimToNull(dto.getId()));
    supp.setShop(trimToNull(dto.getShop()));
    supp.setName(trimToNull(dto.getName()));
    supp.setInactive(dto.isInactive());
    supp.setPrice(dto.getPrice());
    supp.setPortionSize(dto.getPortionSize());
    supp.setSupplementType(trimToNull(dto.getSupplementType()));
    supp.setOvp(dto.getOvp() != null ? dto.getOvp() : 0d);
    supp.setDiscount(dto.getDiscount());
    supp.setMhdProdukt(dto.isMhdProdukt());
    supp.setInBenutzung(dto.isInBenutzung());
    supp.setNonDaily(dto.isNonDaily());
    supp.setConsumptionIntervalDays(dto.getConsumptionIntervalDays() > 1 ? dto.getConsumptionIntervalDays() : 1);

    // Map stock batches
    List<StockBatch> stockBatches = new ArrayList<>();
    if (dto.getStockBatches() != null) {
      for (StockBatchDto bDto : dto.getStockBatches()) {
        if (bDto == null) continue;
        StockBatch batch = new StockBatch();
        batch.setFlavor(trimToNull(bDto.getFlavor()));
        batch.setExpiryDate(parseDate(bDto.getExpiryDate()));
        batch.setAddedDate(parseDate(bDto.getAddedDate()) != null ? parseDate(bDto.getAddedDate()) : LocalDate.now());
        batch.setQuantity(bDto.getQuantity() > 0 ? bDto.getQuantity() : 1);
        batch.setRemaining(bDto.getRemaining());
        stockBatches.add(batch);
      }
    }
    supp.setStockBatches(stockBatches);

    // Derive flavors from batches (unique, sorted, non-blank)
    List<String> flavors = stockBatches.stream()
        .map(StockBatch::getFlavor)
        .filter(f -> f != null && !f.isBlank())
        .distinct()
        .sorted()
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    supp.setFlavors(flavors);

    List<Ingredient> ingredients = new ArrayList<>();
    if (dto.getIngredients() != null) {
      for (IngredientDto ingDto : dto.getIngredients()) {
        Ingredient ing = toEntity(ingDto);
        if (ing != null) {
          ingredients.add(ing);
        }
      }
    }
    supp.setIngredients(ingredients);
    return supp;
  }

  private static Ingredient toEntity(IngredientDto dto) {
    if (dto == null) {
      return null;
    }
    Ingredient ing = new Ingredient();
    ing.setName(trimToNull(dto.getName()));
    ing.setMg(dto.getMg());

    List<Ingredient> sub = new ArrayList<>();
    if (dto.getSubIngredients() != null) {
      for (IngredientDto subDto : dto.getSubIngredients()) {
        Ingredient subIng = toEntity(subDto);
        if (subIng != null) {
          sub.add(subIng);
        }
      }
    }
    ing.setSubIngredients(sub);
    return ing;
  }

  private static LocalDate parseDate(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return LocalDate.parse(s.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private static String trimToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }
}


