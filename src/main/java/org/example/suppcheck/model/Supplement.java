package org.example.suppcheck.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Supplement class it contains Ingredients.
 */

@Getter
@Setter
@Document(collection = "supplements")
public class Supplement {

  @Id
  private String name;
  private List<Ingredient> ingredients = new ArrayList<>();
  private boolean isInactive;
  private double price;
  private int portionSize;

  /**
   * Selbst geschrieben, da hier das ein mutable Objekt weitergegeben wird.
   *
   * @return list of ingredients
   */
  public List<Ingredient> getIngredients() {
    return new ArrayList<>(ingredients);
  }

  /**
   * Selbst geschrieben, da hier das ein mutable Objekt weitergegeben wird.
   */
  public void setIngredients(List<Ingredient> ingredients) {
    this.ingredients = new ArrayList<>(ingredients);
  }

}