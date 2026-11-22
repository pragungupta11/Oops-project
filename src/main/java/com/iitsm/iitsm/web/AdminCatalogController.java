package com.iitsm.iitsm.web;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.iitsm.iitsm.exception.CatalogException;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.service.CategoryCatalog;

/** Admin: manage IT services and incident categories. */
@Controller
@RequestMapping("/admin")
public class AdminCatalogController {

    public record ServiceRow(String name, int criticality, Set<String> dependsOn, boolean active, long incidents) {}

    public record CategoryRow(String name, String keywords, boolean active, boolean builtIn, long incidents) {}

    private final ServiceCatalog services;
    private final CategoryCatalog categories;
    private final IncidentPersistenceService incidents;

    public AdminCatalogController(ServiceCatalog services, CategoryCatalog categories,
                                  IncidentPersistenceService incidents) {
        this.services = services;
        this.categories = categories;
        this.incidents = incidents;
    }

    // ---------------------------------------------------------------- services

    @GetMapping("/services")
    public String services(Model model) {
        List<Incident> all = incidents.findAll();
        Map<String, Long> perService = all.stream().filter(i -> i.getServiceName() != null)
                .collect(Collectors.groupingBy(Incident::getServiceName, Collectors.counting()));
        model.addAttribute("rows", services.allIncludingInactive().stream()
                .map(e -> new ServiceRow(e.getName(), e.getCriticality(), split(e.getDependsOn()), e.isActive(),
                        perService.getOrDefault(e.getName(), 0L)))
                .toList());
        model.addAttribute("names", services.allIncludingInactive().stream().map(e -> e.getName()).toList());
        return "admin/services";
    }

    @PostMapping("/services")
    public String addService(@RequestParam("name") String name, @RequestParam("criticality") int criticality,
                             @RequestParam(name = "dependsOn", required = false) Set<String> dependsOn,
                             RedirectAttributes redirect) {
        try {
            services.add(name, criticality, dependsOn);
            redirect.addFlashAttribute("message", "Service " + name.trim() + " added.");
        } catch (CatalogException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/services";
    }

    @PostMapping("/services/{name}")
    public String updateService(@PathVariable("name") String name, @RequestParam("criticality") int criticality,
                                @RequestParam(name = "dependsOn", required = false) Set<String> dependsOn,
                                @RequestParam(name = "active", defaultValue = "false") boolean active,
                                RedirectAttributes redirect) {
        try {
            services.update(name, criticality, dependsOn, active);
            redirect.addFlashAttribute("message", "Service " + name + " saved.");
        } catch (CatalogException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/services";
    }

    // ---------------------------------------------------------------- categories

    @GetMapping("/categories")
    public String categories(Model model) {
        Map<String, Long> perCategory = incidents.findAll().stream()
                .collect(Collectors.groupingBy(Incident::getCategory, Collectors.counting()));
        model.addAttribute("rows", categories.all().stream()
                .map(c -> new CategoryRow(c.name(), c.keywords(), c.active(), c.builtIn(),
                        perCategory.getOrDefault(c.name(), 0L)))
                .toList());
        return "admin/categories";
    }

    @PostMapping("/categories")
    public String addCategory(@RequestParam("name") String name,
                              @RequestParam(name = "keywords", defaultValue = "") String keywords,
                              RedirectAttributes redirect) {
        try {
            categories.add(name, keywords);
            redirect.addFlashAttribute("message", "Category " + name.trim() + " added. Give at least one technician "
                    + "this skill (Users page), otherwise its incidents wait in the queue.");
        } catch (CatalogException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{name}")
    public String updateCategory(@PathVariable("name") String name,
                                 @RequestParam(name = "keywords", defaultValue = "") String keywords,
                                 @RequestParam(name = "active", defaultValue = "false") boolean active,
                                 RedirectAttributes redirect) {
        try {
            categories.update(name, keywords, active);
            redirect.addFlashAttribute("message", "Category " + name + " saved.");
        } catch (CatalogException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    private static Set<String> split(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return java.util.Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
