package org.example.suppcheck.model;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One historical price entry for a {@link Supplement}.
 */
@Setter
@Getter
public class PriceEntry {

  private LocalDate date;
  private double price;
}
