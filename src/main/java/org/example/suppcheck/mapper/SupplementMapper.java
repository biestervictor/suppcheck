package org.example.suppcheck.mapper;

import java.util.ArrayList;
import java.util.List;
import org.example.suppcheck.dto.IngredientDto;
import org.example.suppcheck.dto.SupplementSaveDto;
import org.example.suppcheck.model.Ingredient;
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
    supp.setNonDaily(dto.isNonDaily());
    supp.setConsumptionIntervalDays(dto.getConsumptionIntervalDays() > 1 ? dto.getConsumptionIntervalDays() : 1);

    List<String> flavors = new ArrayList<>();
    if (dto.getFlavors() != null) {
      for (String f : dto.getFlavors()) {
        if (f != null && !f.isBlank()) {
          flavors.add(f.trim());
        }
      }
    }
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

  private static String trimToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }
}

