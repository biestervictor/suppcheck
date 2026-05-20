package org.example.suppcheck.controller;

import org.example.suppcheck.model.Hersteller;
import org.example.suppcheck.service.HerstellerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/hersteller")
public class HerstellerController {

    private final HerstellerService herstellerService;

    public HerstellerController(HerstellerService herstellerService) {
        this.herstellerService = herstellerService;
    }

    @GetMapping
    public String showManage(Model model) {
        model.addAttribute("hersteller", herstellerService.findAll());
        model.addAttribute("newHersteller", new Hersteller());
        return "hersteller_manage";
    }

    @PostMapping("/add")
    public String add(@RequestParam String name, RedirectAttributes ra) {
        try {
            herstellerService.add(name);
            ra.addFlashAttribute("successMsg", "Hersteller '" + name.trim() + "' wurde hinzugefügt.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/hersteller";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable String id, RedirectAttributes ra) {
        herstellerService.delete(id);
        ra.addFlashAttribute("successMsg", "Hersteller wurde gelöscht.");
        return "redirect:/hersteller";
    }
}
