package org.example.suppcheck.model;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One historical price entry for a {@link Supplement}.
 * Contains both the actual price and the OVP (Original-Verpackungspreis).
 */
@Setter
@Getter
public class PriceEntry {

  private LocalDate date;
  private double price;
  private double ovp;
}
