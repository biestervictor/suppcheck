package org.example.suppcheck.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Ingredient is a class that represents an ingredient in a supplement.
 */
@Setter
@Getter
public class Ingredient {

  private String name;
  private double mg;
  private List<Ingredient> subIngredients = new ArrayList<>();

}