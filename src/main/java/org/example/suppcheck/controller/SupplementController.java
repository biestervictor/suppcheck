package org.example.suppcheck.controller;

import java.util.*;
import java.util.stream.Collectors;

import org.example.suppcheck.model.Ingredient;
import org.example.suppcheck.model.Shop;
import org.example.suppcheck.model.Supplement;
import org.example.suppcheck.model.SupplementType;
import org.example.suppcheck.service.SupplementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
        List<String> types = Arrays.stream(SupplementType.values())
                .map(SupplementType::name)
                .collect(Collectors.toList());
        model.addAttribute("types", types);
        List<String> shops = Arrays.stream(Shop.values())
                .map(Shop::name)
                .collect(Collectors.toList());
        model.addAttribute("shops", shops);
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
        List<String> types = Arrays.stream(SupplementType.values())
                .map(SupplementType::name)
                .collect(Collectors.toList());
        model.addAttribute("types", types);

        List<String> shops = Arrays.stream(Shop.values())
                .map(Shop::name)
                .collect(Collectors.toList());
        model.addAttribute("shops", shops);
        return "supplement_form";
    }

    /**
     * Delete a supplement.
     *
     * @param name the name of the supplement to delete
     * @return a redirect to the supplements list
     */
    @PostMapping("/delete/{id}")
    public String deleteSupplement(@PathVariable String id) {
        supplementService.deleteSupplementById(id);
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

        double preisProTag = 0;
        double preisWorkout = 0;
        double preisProTagExtended = 0;
        for (Supplement supp : supplements) {


             if( !supp.isInactive() && supp.getSupplementType().equals(SupplementType.BASIC.name())) {
                preisProTag += supp.getPrice() / supp.getPortionSize();
            }else  if( !supp.isInactive() && supp.getSupplementType().equals(SupplementType.EXTENDED.name())) {
                 preisProTagExtended += supp.getPrice() / supp.getPortionSize();
             }else if (!supp.isInactive()){
                preisWorkout += supp.getPrice() / supp.getPortionSize();
            }



        }
        double preisProMonat = preisProTag*30+preisWorkout*15;
        model.addAttribute("preisProMonat", preisProMonat);
        model.addAttribute("preisProTagExtended", preisProTagExtended);
        model.addAttribute("preisProTag", preisProTag);
        model.addAttribute("preisProWorkout", preisWorkout);
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
    public String showIngredientsSummaryWithWorkout(@RequestParam(defaultValue = "false")boolean isWorkoutDay, Model model){
        List<Supplement> supplements = supplementService.getAllSupplements();
        List<Ingredient> summedIngredients = supplementService.getSummedIngredients(supplements,isWorkoutDay);
        model.addAttribute("summedIngredients", summedIngredients);
        model.addAttribute("isWorkoutDay", isWorkoutDay);
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

        // Supplement-Details werden geloggt, um alle Inhalte anzuzeigen
        System.out.println("Speichere Supplement: " + supplement);
        if (supplement.getIngredients() != null) {
            for (Ingredient ingredient : supplement.getIngredients()) {
                System.out.println("  Ingredient: " + ingredient.getName());
                System.out.println("  Ingredient: " + ingredient.getMg());
            }
        }
        System.out.println("  Typ: " + supplement.getSupplementType());
        System.out.println("  Shop: " + supplement.getShop());
        System.out.println("  Preis: " + supplement.getPrice());
        System.out.println("  Portionsgröße: " + supplement.getPortionSize());
        System.out.println("  Inaktiv: " + supplement.isInactive());

        supplementService.saveSupplement(supplement);
        return "redirect:/supplements/new?success";
    }
}