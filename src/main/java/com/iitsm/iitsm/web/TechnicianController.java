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
import com.iitsm.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.service.SlaService;

/** Technician pages. A technician's login username is also his Technician id. */
@Controller
@RequestMapping("/technician")
public class TechnicianController {
    private final IncidentWorkflowService workflow;
    private final IncidentPersistenceService incidents;
    private final SlaService slaService;
    private final IncidentTimeline timeline;
    private final IncidentConversationService conversation;

    public TechnicianController(IncidentWorkflowService workflow, IncidentPersistenceService incidents,
                                SlaService slaService,
                                IncidentTimeline timeline,
                                IncidentConversationService conversation) {
        this.workflow = workflow;
        this.incidents = incidents;
        this.slaService = slaService;
        this.timeline = timeline;
        this.conversation = conversation;
    }

    /** The suspected original incident, shown next to the "Link" / "Not the same" buttons. */
    public record SuggestionView(String id, String match, String description, String technicianName, String status) {}

    @GetMapping("/incidents")
    public String list(Authentication authentication, Model model) {
        String me = authentication.getName();
        List<IncidentView> rows = incidents.findAll().stream()
                .filter(i -> i.getAssignedTechnician() != null && me.equals(i.getAssignedTechnician().getId()))
                .map(i -> IncidentView.of(i, slaService))
                .sorted(Comparator.comparing((IncidentView v) -> !v.open())
                        .thenComparing(IncidentView::resolutionDueIso))
                .toList();
        model.addAttribute("rows", rows);
        return "technician/incidents";
    }

    @GetMapping("/incidents/{id}")
    public String detail(@PathVariable("id") String id, Authentication authentication, Model model) {
        Incident incident = find(id);
        if (incident.getAssignedTechnician() == null
                || !authentication.getName().equals(incident.getAssignedTechnician().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This incident is not assigned to you");
        }
        model.addAttribute("incident", IncidentView.of(incident, slaService));
        model.addAttribute("categories", Category.values());
        model.addAttribute("history", timeline.forIncident(incident.getId(), true));
        model.addAttribute("thread", conversation.thread(incident.getId(), true));
        model.addAttribute("awaitingInfo", conversation.isAwaitingInfo(incident.getId()));
        model.addAttribute("feedback", conversation.feedbackFor(incident.getId()).orElse(null));

        // Acknowledging late is allowed (work must continue), but the technician should know.
        long minutesLate = 0;
        if (incident.getAcknowledgedAt() == null && incident.getPriority() != null) {
            java.time.LocalDateTime due = slaService.responseDeadline(incident.getCreatedAt(), incident.getPriority());
            if (java.time.LocalDateTime.now().isAfter(due)) {
                minutesLate = Math.max(1, java.time.Duration.between(due, java.time.LocalDateTime.now()).toMinutes());
            }
        }
        model.addAttribute("responseMinutesLate", minutesLate);
        model.addAttribute("pendingCategory", incident.getRequestedCategory());
        model.addAttribute("pendingReason", incident.getReassignmentReason());

        // Duplicate hint (only while not linked): show what the suspected original is about.
        if (incident.hasDuplicateSuggestion() && !incident.isLinked()) {
            try {
                Incident original = incidents.findById(incident.getSuggestedParentId());
                model.addAttribute("suggestion", new SuggestionView(original.getId(),
                        incident.getSuggestedMatchPercent() + "%", original.getDescription(),
                        original.getAssignedTechnician() == null ? "-" : original.getAssignedTechnician().getName(),
                        original.getStatus().name()));
            } catch (IllegalArgumentException ignored) {
                // the suggested incident no longer exists; just show nothing
            }
        }
        model.addAttribute("linkedReports", incidents.findAll().stream()
                .filter(i -> incident.getId().equals(i.getParentId()))
                .map(Incident::getId)
                .toList());
        return "technician/incident-detail";
    }

    @PostMapping("/incidents/{id}/acknowledge")
    public String acknowledge(@PathVariable("id") String id, Authentication authentication,
                              RedirectAttributes redirect) {
        try {
            workflow.acknowledge(id, authentication.getName());
            redirect.addFlashAttribute("message", "Incident acknowledged. The response clock has stopped.");
        } catch (InvalidStateTransitionException | InvalidIncidentDataException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return "redirect:/technician/incidents/" + id;
    }

    @PostMapping("/incidents/{id}/start")
    public String start(@PathVariable("id") String id, Authentication authentication,
                        RedirectAttributes redirect) {
        try {
            workflow.start(id, authentication.getName());
            redirect.addFlashAttribute("message", "Work started.");
        } catch (InvalidStateTransitionException | InvalidIncidentDataException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return "redirect:/technician/incidents/" + id;
    }

    @PostMapping("/incidents/{id}/resolve")
    public String resolve(@PathVariable("id") String id,
                          @RequestParam("resolution") String resolution,
                          Authentication authentication, RedirectAttributes redirect) {
        try {
            workflow.resolve(id, authentication.getName(), resolution);
            redirect.addFlashAttribute("message", "Incident resolved.");
        } catch (InvalidStateTransitionException | InvalidIncidentDataException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return "redirect:/technician/incidents/" + id;
    }

    /** Wrong category: ask the admin to re-route this incident to the right kind of technician. */
    @PostMapping("/incidents/{id}/reassign")
    public String requestReassignment(@PathVariable("id") String id,
                                      @RequestParam("category") String category,
                                      @RequestParam("reason") String reason,
                                      Authentication authentication, RedirectAttributes redirect) {
        try {
            String label = Category.valueOf(category).label();
            workflow.requestReassignment(id, authentication.getName(), label, reason);
            redirect.addFlashAttribute("message",
                    "Re-routing to " + label + " requested. The admin will review it; keep the incident until then.");
        } catch (InvalidStateTransitionException | InvalidIncidentDataException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return "redirect:/technician/incidents/" + id;
    }

    @PostMapping("/incidents/{id}/duplicate/link")
    public String linkDuplicate(@PathVariable("id") String id, Authentication authentication,
                                RedirectAttributes redirect) {
        try {
            Incident linked = workflow.linkToSuggestedParent(id, authentication.getName());
            redirect.addFlashAttribute("message", id + " is now linked to " + linked.getParentId()
                    + ". It will follow that incident and no longer counts towards your workload.");
        } catch (InvalidStateTransitionException | InvalidIncidentDataException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        // after linking, the report may now belong to another technician
        return "redirect:/technician/incidents";
    }

    @PostMapping("/incidents/{id}/duplicate/dismiss")
    public String dismissDuplicate(@PathVariable("id") String id, Authentication authentication,
                                   RedirectAttributes redirect) {
        try {
            workflow.dismissDuplicateSuggestion(id, authentication.getName());
            redirect.addFlashAttribute("message", "Noted: " + id + " is a separate problem.");
        } catch (InvalidIncidentDataException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return "redirect:/technician/incidents/" + id;
    }

    @PostMapping("/incidents/{id}/duplicate/unlink")
    public String unlink(@PathVariable("id") String id, Authentication authentication,
                         RedirectAttributes redirect) {
        try {
            Incident unlinked = workflow.unlink(id, authentication.getName());
            redirect.addFlashAttribute("message", id + " is a separate incident again and was assigned to "
                    + unlinked.getAssignedTechnician().getName() + ".");
        } catch (InvalidStateTransitionException | InvalidIncidentDataException
                 | com.iitsm.iitsm.exception.TechnicianUnavailableException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/technician/incidents/" + id;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return "redirect:/technician/incidents";
    }

    /** Message to the employee, internal investigation note, or a request for more information. */
    @PostMapping("/incidents/{id}/comment")
    public String addComment(@PathVariable("id") String id, @RequestParam("kind") String kind,
                             @RequestParam("text") String text,
                             Authentication authentication, RedirectAttributes redirect) {
        com.iitsm.iitsm.model.CommentKind k;
        try {
            k = com.iitsm.iitsm.model.CommentKind.valueOf(kind);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "Choose what kind of entry this is");
            return "redirect:/technician/incidents/" + id;
        }
        try {
            conversation.addTechnicianEntry(id, authentication.getName(), k, text);
            redirect.addFlashAttribute("message", switch (k) {
                case NOTE -> "Investigation note saved (only staff can see it).";
                case MESSAGE -> "Message sent to the employee.";
                case INFO_REQUEST -> "Information requested. The employee has been notified.";
            });
        } catch (InvalidIncidentDataException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return "redirect:/technician/incidents/" + id;
    }

    private Incident find(String id) {
        try {
            return incidents.findById(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
    }
}
