package org.example.suppcheck.model;

import lombok.Getter;
import lombok.Setter;

/**
  * Ingredient is a class that represents an ingredient in a supplement.
 */

@Getter
@Setter

public class Ingredient {
  private String name;
  private double mg;
}