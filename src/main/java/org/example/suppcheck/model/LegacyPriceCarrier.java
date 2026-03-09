package org.example.suppcheck.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Transient;

/**
 * Transitional legacy field carrier.
 *
 * <p>Old documents stored a root-level field {@code price}. We keep reading it during migration,
 * but we don't want to persist it anymore.</p>
 */
@Setter
@Getter
public abstract class LegacyPriceCarrier {

  /**
   * Legacy price field from old MongoDB documents.
   */
  @Transient
  private Double legacyPrice;
}
