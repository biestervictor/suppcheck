package org.example.suppcheck.controller;

import org.example.suppcheck.service.SupplementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/lagerbestand")
public class LagerbestandController {

    private final SupplementService supplementService;

    public LagerbestandController(SupplementService supplementService) {
        this.supplementService = supplementService;
    }

    @GetMapping
    public String showPage(Model model) {
        model.addAttribute("supplements", supplementService.getAllSupplements());
        return "lagerbestand";
    }
}
