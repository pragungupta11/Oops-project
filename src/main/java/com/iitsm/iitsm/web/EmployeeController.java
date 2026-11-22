package com.iitsm.iitsm.web;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.service.SlaService;
import com.iitsm.iitsm.strategy.category.CategorySuggester;

@Controller
@RequestMapping("/employee")
public class EmployeeController {
    private final IncidentWorkflowService workflow;
    private final IncidentPersistenceService incidents;
    private final ServiceCatalog catalog;
    private final com.iitsm.iitsm.service.CategoryCatalog categories;
    private final SlaService slaService;
    private final CategorySuggester categorySuggester;
    private final IncidentTimeline timeline;
    private final IncidentConversationService conversation;
    private final com.iitsm.iitsm.service.AutoCloseJob autoClose;

    public EmployeeController(IncidentWorkflowService workflow, IncidentPersistenceService incidents,
                              ServiceCatalog catalog, SlaService slaService,
                              CategorySuggester categorySuggester,
                              IncidentTimeline timeline,
                              IncidentConversationService conversation,
                              com.iitsm.iitsm.service.AutoCloseJob autoClose,
                              com.iitsm.iitsm.service.CategoryCatalog categories) {
        this.workflow = workflow;
        this.incidents = incidents;
        this.catalog = catalog;
        this.slaService = slaService;
        this.categorySuggester = categorySuggester;
        this.timeline = timeline;
        this.conversation = conversation;
        this.autoClose = autoClose;
        this.categories = categories;
    }

    /** What the report form shows under the category box. */
    public record SuggestionResponse(String category, String label, String source, String reason) {}

    /**
     * Called by the report form while the employee types. 200 with a suggestion, or
     * 204 (no content) when there is no confident guess.
     */
    @GetMapping("/suggest-category")
    @ResponseBody
    public ResponseEntity<SuggestionResponse> suggestCategory(@RequestParam("description") String description) {
        return categorySuggester.suggest(description)
                .map(s -> ResponseEntity.ok(new SuggestionResponse(s.category(), s.category(),
                        s.source(), s.reason())))
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Small line shown in the "possible duplicates" warning. Details are only filled in for the
     * employee's OWN earlier incidents; other people's incidents stay private.
     */
    public record DuplicateView(boolean own, String id, String description, String score) {}

    @GetMapping("/report")
    public String reportForm(Model model) {
        fillForm(model, "", categories.activeNames().get(0), 2, 2, catalog.all().get(0).getName());
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
                    authentication.getName(), description, category,
                    impact, urgency, serviceName);

            String me = authentication.getName();
            List<DuplicateView> mine = outcome.duplicates().stream()
                    .filter(m -> me.equals(m.incident().getReporterUsername()))
                    .map(m -> new DuplicateView(true, m.incident().getId(), m.incident().getDescription(),
                            Math.round(m.score() * 100) + "%"))
                    .toList();
            redirect.addFlashAttribute("justCreated", true);
            redirect.addFlashAttribute("queued", outcome.incident().getAssignedTechnician() == null);
            redirect.addFlashAttribute("duplicates", mine);
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

    /** "Yes, it's fixed": the employee confirms and the incident is closed. */
    @PostMapping("/incidents/{id}/confirm")
    public String confirm(@PathVariable("id") String id, Authentication authentication, RedirectAttributes redirect) {
        try {
            workflow.confirmResolution(id, authentication.getName());
            redirect.addFlashAttribute("message", "Great, glad it is fixed! The incident is now closed.");
        } catch (InvalidIncidentDataException | com.iitsm.iitsm.exception.InvalidStateTransitionException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return "redirect:/employee/incidents/" + id;
    }

    /** "No, still broken": back to the technician with the employee's reason. */
    @PostMapping("/incidents/{id}/reopen")
    public String reopen(@PathVariable("id") String id, @RequestParam("reason") String reason,
                         Authentication authentication, RedirectAttributes redirect) {
        try {
            workflow.reopen(id, authentication.getName(), reason);
            redirect.addFlashAttribute("message", "Sorry about that. The incident is back with your technician.");
        } catch (InvalidIncidentDataException | com.iitsm.iitsm.exception.InvalidStateTransitionException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return "redirect:/employee/incidents/" + id;
    }

    private static String readable(java.time.Duration d) {
        long days = d.toDays();
        if (days >= 1 && d.equals(java.time.Duration.ofDays(days))) return days + (days == 1 ? " day" : " days");
        long hours = d.toHours();
        if (hours >= 1) return hours + (hours == 1 ? " hour" : " hours");
        return d.toMinutes() + " minutes";
    }

    /** Employee adds information or answers the technician's question. */
    @PostMapping("/incidents/{id}/message")
    public String addMessage(@PathVariable("id") String id, @RequestParam("text") String text,
                             Authentication authentication, RedirectAttributes redirect) {
        try {
            conversation.addEmployeeMessage(id, authentication.getName(), text);
            redirect.addFlashAttribute("message", "Sent. Your technician has been notified.");
        } catch (InvalidIncidentDataException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return "redirect:/employee/incidents/" + id;
    }

    /** Employee rates the service after resolution (1-5 stars). */
    @PostMapping("/incidents/{id}/feedback")
    public String giveFeedback(@PathVariable("id") String id, @RequestParam("rating") int rating,
                               @RequestParam(name = "comment", required = false) String comment,
                               Authentication authentication, RedirectAttributes redirect) {
        try {
            conversation.giveFeedback(id, authentication.getName(), rating, comment);
            redirect.addFlashAttribute("message", "Thank you for your feedback!");
        } catch (InvalidIncidentDataException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return "redirect:/employee/incidents/" + id;
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
        model.addAttribute("history", timeline.forIncident(incident.getId(), false));
        model.addAttribute("thread", conversation.thread(incident.getId(), false));
        model.addAttribute("awaitingInfo", conversation.isAwaitingInfo(incident.getId()));
        model.addAttribute("feedback", conversation.feedbackFor(incident.getId()).orElse(null));
        model.addAttribute("canConfirm", incident.getStatus() == com.iitsm.iitsm.model.IncidentStatus.RESOLVED
                && !incident.isLinked());
        model.addAttribute("autoCloseText", readable(autoClose.autoCloseAfter()));
        model.addAttribute("canMessage", incident.getStatus() != com.iitsm.iitsm.model.IncidentStatus.CLOSED);
        return "employee/incident-detail";
    }

    private void fillForm(Model model, String description, String category,
                          int impact, int urgency, String serviceName) {
        model.addAttribute("categories", categories.activeNames());
        model.addAttribute("services", catalog.all());
        model.addAttribute("description", description);
        model.addAttribute("category", category);
        model.addAttribute("impact", impact);
        model.addAttribute("urgency", urgency);
        model.addAttribute("serviceName", serviceName);
    }
}
