package org.example.suppcheck.controller;

import java.util.ArrayList;
import java.util.List;
import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.service.SupplementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for the Supplement entity.
 */

@Controller
@RequestMapping("/supplements")
public class SupplementController {

  private final SupplementService supplementService;

  /**
   * Constructor for SupplementController.
   *
   * @param supplementService the SupplementService instance
   */
  @SuppressWarnings("java:S2384")
  public SupplementController(SupplementService supplementService) {
    this.supplementService = supplementService;
  }

  /**
   * Shows a Form to create a new Supplement.
   *
   * @param model the model to add attributes to
   * @return the name of the view to render
   */
  @GetMapping("/new")
  public String showCreateForm(Model model) {
    Supplement supplement = new Supplement();
    supplement.setIngredients(new ArrayList<>());
    Ingredient ingredient = new Ingredient();

    supplement.getIngredients().add(ingredient);
    model.addAttribute("supplement", supplement);
    return "supplement_form";
  }

  /**
   * Shows the edit Supplemtn page.
   *
   * @param name  the name of the supplement to edit
   * @param model the model to add attributes to
   * @return the name of the view to render
   */
  @GetMapping("/edit/{name}")
  public String editSupplement(@PathVariable String name, Model model) {
    Supplement supplement = supplementService.getSupplementById(name).orElseThrow();
    model.addAttribute("supplement", supplement);
    return "supplement_form";
  }

  /**
   * Delete a supplement.
   *
   * @param name the name of the supplement to delete
   * @return a redirect to the supplements list
   */
  @PostMapping("/delete/{name}")
  public String deleteSupplement(@PathVariable String name) {
    supplementService.deleteSupplementByName(name);
    return "redirect:/supplements";
  }

  /**
   * Shows the all supplements.
   *
   * @param model the model to add attributes to
   * @return the name of the view to render
   */
  @GetMapping
  public String showSupplementsList(Model model) {
    List<Supplement> supplements = supplementService.getAllSupplements();
    model.addAttribute("supplements", supplements);
    return "supplements_list";
  }

  /**
   * Shows daily Intake.
   *
   * @param model the model to add attributes to
   * @return the name of the view to render
   */
  @GetMapping("/ingredients/summary")
  public String showIngredientsSummary(Model model) {
    List<Supplement> supplements = supplementService.getAllSupplements();
    List<Ingredient> summedIngredients = supplementService.getSummedIngredients(supplements);
    model.addAttribute("summedIngredients", summedIngredients);
    return "ingredients_summary";
  }

  /**
   * Stores or edits supplements.
   *
   * @param supplement the supplement to save
   * @return a redirect to the new supplement form with a success message
   */
  @PostMapping("/save")
  public String saveSupplement(@ModelAttribute Supplement supplement) {
    supplementService.saveSupplement(supplement);
    return "redirect:/supplements/new?success";
  }
}