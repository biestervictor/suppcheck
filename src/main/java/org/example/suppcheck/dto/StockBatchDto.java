package org.example.suppcheck.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO für einen StockBatch im Supplement-Bearbeitungsformular.
 * expiryDate und addedDate werden als ISO-String "yyyy-MM-dd" übergeben.
 */
@Getter
@Setter
public class StockBatchDto {

  /** Flavor/Geschmacksrichtung – leer/null = kein Flavor. */
  private String flavor;

  /** MHD als ISO-String "yyyy-MM-dd" – leer/null = kein MHD. */
  private String expiryDate;

  /** Datum, an dem der Batch hinzugefügt wurde (preserved, nicht editierbar). */
  private String addedDate;

  /** Originalmengen beim Anlegen (preserved als Hidden-Field). */
  private int quantity;

  /** Verbleibende Menge – editierbar. null = Legacy. */
  private Integer remaining;

  /** True wenn dieser Batch/Flavor aktuell in Benutzung ist. */
  private boolean inBenutzung;
}
