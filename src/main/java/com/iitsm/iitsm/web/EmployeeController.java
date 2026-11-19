package com.iitsm.iitsm.web;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.service.SlaService;

@Controller
@RequestMapping("/employee")
public class EmployeeController {
    private final IncidentWorkflowService workflow;
    private final IncidentPersistenceService incidents;
    private final ServiceCatalog catalog;
    private final SlaService slaService;

    public EmployeeController(IncidentWorkflowService workflow, IncidentPersistenceService incidents,
                              ServiceCatalog catalog, SlaService slaService) {
        this.workflow = workflow;
        this.incidents = incidents;
        this.catalog = catalog;
        this.slaService = slaService;
    }

    /** Small line shown in the "possible duplicates" warning. */
    public record DuplicateView(String id, String description, String score) {}

    @GetMapping("/report")
    public String reportForm(Model model) {
        fillForm(model, "", Category.NETWORK.name(), 2, 2, catalog.all().get(0).getName());
        return "employee/report";
    }

    @PostMapping("/report")
    public String submit(@RequestParam("description") String description,
                         @RequestParam("category") String category,
                         @RequestParam("impact") int impact,
                         @RequestParam("urgency") int urgency,
                         @RequestParam("serviceName") String serviceName,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirect) {
        try {
            IncidentWorkflowService.ReportOutcome outcome = workflow.report(
                    authentication.getName(), description, Category.valueOf(category),
                    impact, urgency, serviceName);

            List<DuplicateView> duplicates = outcome.duplicates().stream()
                    .map(m -> new DuplicateView(m.incident().getId(), m.incident().getDescription(),
                            Math.round(m.score() * 100) + "%"))
                    .toList();
            redirect.addFlashAttribute("justCreated", true);
            redirect.addFlashAttribute("duplicates", duplicates);
            // Redirect so that refreshing the page does not file the same incident twice.
            return "redirect:/employee/incidents/" + outcome.incident().getId();
        } catch (InvalidIncidentDataException | TechnicianUnavailableException | IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            fillForm(model, description, category, impact, urgency, serviceName);
            return "employee/report";
        }
    }

    @GetMapping("/incidents")
    public String list(Authentication authentication, Model model) {
        List<IncidentView> rows = incidents.findAll().stream()
                .filter(i -> authentication.getName().equals(i.getReporterUsername()))
                .sorted(Comparator.comparing(Incident::getCreatedAt).reversed())
                .map(i -> IncidentView.of(i, slaService))
                .toList();
        model.addAttribute("rows", rows);
        return "employee/incidents";
    }

    @GetMapping("/incidents/{id}")
    public String detail(@PathVariable("id") String id, Authentication authentication, Model model) {
        Incident incident;
        try {
            incident = incidents.findById(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        if (!authentication.getName().equals(incident.getReporterUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your incident");
        }
        model.addAttribute("incident", IncidentView.of(incident, slaService));
        return "employee/incident-detail";
    }

    private void fillForm(Model model, String description, String category,
                          int impact, int urgency, String serviceName) {
        model.addAttribute("categories", Category.values());
        model.addAttribute("services", catalog.all());
        model.addAttribute("description", description);
        model.addAttribute("category", category);
        model.addAttribute("impact", impact);
        model.addAttribute("urgency", urgency);
        model.addAttribute("serviceName", serviceName);
    }
}
