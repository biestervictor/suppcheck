package org.example.suppcheck.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class SupplementSaveDto {

  private String id;
  private String shop;
  private String name;
  private List<IngredientDto> ingredients = new ArrayList<>();
  private boolean isInactive;
  private double price;
  private int portionSize;
  private String supplementType;
  private Double ovp;
  private Double discount;
  private boolean mhdProdukt;

  /**
   * True wenn dieses Supplement nicht täglich eingenommen wird.
   */
  private boolean nonDaily;

  /**
   * Einnahmeintervall in Tagen (nur relevant wenn nonDaily = true).
   */
  private int consumptionIntervalDays = 1;

  /**
   * Verfügbare Flavors für dieses Supplement.
   */
  private List<String> flavors = new ArrayList<>();

  // ── Erster Bestand (nur beim Anlegen, optional) ──────────────────────────

  /** Flavor des ersten Batches (leer = kein Flavor). */
  private String initialFlavor;

  /** MHD des ersten Batches als ISO-String "yyyy-MM-dd" (leer = kein MHD). */
  private String initialMhd;

  /** Menge des ersten Batches (0 = kein Batch anlegen). */
  private int initialQty = 0;

}
