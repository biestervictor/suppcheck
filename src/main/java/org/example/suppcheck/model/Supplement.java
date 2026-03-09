package org.example.suppcheck.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Supplement class it contains Ingredients.
 */

@Setter
@Getter
@Document(collection = "supplements")
public class Supplement {

  @Id
  private String id;
  private String shop;
  private String name;
  private List<Ingredient> ingredients = new ArrayList<>();
  private boolean isInactive;

  /**
   * Price history. The current price is the last element.
   */
  private List<PriceEntry> prices = new ArrayList<>();

  private int portionSize;
  private String supplementType = SupplementType.BASIC.name();

  /**
   * Compatibility helper: returns the latest price or 0 when none is present.
   */
  public double getPrice() {
    if (prices == null || prices.isEmpty()) {
      return 0d;
    }
    PriceEntry last = prices.get(prices.size() - 1);
    return last != null ? last.getPrice() : 0d;
  }

  /**
   * Compatibility helper: when a price is set, append a new entry for today's date.
   */
  public void setPrice(double price) {
    addPriceForToday(price);
  }

  public void addPriceForToday(double price) {
    if (prices == null) {
      prices = new ArrayList<>();
    }
    PriceEntry entry = new PriceEntry();
    entry.setDate(LocalDate.now());
    entry.setPrice(price);
    prices.add(entry);
  }
}
