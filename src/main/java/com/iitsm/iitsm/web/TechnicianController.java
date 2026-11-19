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

    public TechnicianController(IncidentWorkflowService workflow, IncidentPersistenceService incidents,
                                SlaService slaService) {
        this.workflow = workflow;
        this.incidents = incidents;
        this.slaService = slaService;
    }

    @GetMapping("/incidents")
    public String list(Authentication authentication, Model model) {
        String me = authentication.getName();
        List<IncidentView> rows = incidents.findAll().stream()
                .filter(i -> i.getAssignedTechnician() != null && me.equals(i.getAssignedTechnician().getId()))
                .sorted(Comparator.comparing(Incident::getCreatedAt).reversed())
                .map(i -> IncidentView.of(i, slaService))
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

    private Incident find(String id) {
        try {
            return incidents.findById(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
    }
}
