package org.example.suppcheck.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
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
   * Price history. The current price/OVP is the last element.
   */
  private List<PriceEntry> prices = new ArrayList<>();


  /**
   * Price coming from forms/DTOs. This is NOT persisted and must be merged into {@link #prices} by the service.
   */
  @Transient
  private Double currentPrice;

  /**
   * OVP coming from forms/DTOs. This is NOT persisted and must be merged into {@link #prices} by the service.
   */
  @Transient
  private Double currentOvp;

  private int portionSize;
  private String supplementType = SupplementType.BASIC.name();

  /**
   * Rabatt in Prozent.
   */
  private Double discount;

  /**
   * Ist MHD-Produkt (Mindesthaltbarkeitsdatum-Ware).
   */
  private boolean mhdProdukt;

  /**
   * Returns the latest historical price if present.
   */
  public double getPrice() {
    if (prices == null || prices.isEmpty()) {
      return 0d;
    }
    PriceEntry last = prices.getLast();
    return last != null ? last.getPrice() : 0d;
  }

  /**
   * Used by data-binding (Thymeleaf form) / mapper to capture the entered price.
   */
  public void setPrice(double price) {
    this.currentPrice = price;
  }

  /**
   * Returns the latest historical OVP if present.
   */
  public double getOvp() {
    if (prices == null || prices.isEmpty()) {
      return 0d;
    }
    PriceEntry last = prices.getLast();
    return last != null ? last.getOvp() : 0d;
  }

  /**
   * Used by data-binding (Thymeleaf form) / mapper to capture the entered OVP.
   */
  public void setOvp(double ovp) {
    this.currentOvp = ovp;
  }
}
