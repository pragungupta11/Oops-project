package com.iitsm.iitsm.web;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.iitsm.iitsm.exception.AccountException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.Technician;
import com.iitsm.iitsm.persistence.service.TechnicianPersistenceService;
import com.iitsm.iitsm.repository.AppUserRepository;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.security.UserAccountService;

/** Admin: create accounts (incl. technicians with skills), deactivate, manage technicians. */
@Controller
@RequestMapping("/admin")
public class AdminUserController {
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    /** One row of the users table; the technician fields are empty for other roles. */
    public record UserRow(String username, String fullName, String email, String role, boolean enabled,
                          String created, boolean technician, Set<String> skills, boolean available, int active) {}

    /** An open incident whose technician is unavailable. */
    public record StuckRow(String id, String description, String category, String technicianName) {}

    private final AppUserRepository users;
    private final TechnicianPersistenceService technicians;
    private final UserAccountService accounts;
    private final IncidentWorkflowService workflow;
    private final IncidentPersistenceService incidents;

    public AdminUserController(AppUserRepository users, TechnicianPersistenceService technicians,
                               UserAccountService accounts, IncidentWorkflowService workflow,
                               IncidentPersistenceService incidents) {
        this.users = users;
        this.technicians = technicians;
        this.accounts = accounts;
        this.workflow = workflow;
        this.incidents = incidents;
    }

    @GetMapping("/users")
    public String list(@RequestParam(name = "role", defaultValue = "") String role, Model model) {
        Map<String, Technician> techs = technicians.findAll().stream()
                .collect(Collectors.toMap(Technician::getId, Function.identity()));
        List<UserRow> rows = users.findAll().stream()
                .sorted(Comparator.comparing((com.iitsm.iitsm.persistence.entity.AppUserEntity u) -> u.getRole())
                        .thenComparing(u -> u.getUsername()))
                .map(u -> {
                    Technician t = techs.get(u.getUsername());
                    return new UserRow(u.getUsername(), u.getFullName(), u.getEmail() == null ? "-" : u.getEmail(),
                            u.getRole(), u.isEnabled(),
                            u.getCreatedAt() == null ? "-" : FORMAT.format(u.getCreatedAt()),
                            t != null, t == null ? Set.of() : new TreeSet<>(t.getSkills()),
                            t != null && t.isAvailable(), t == null ? 0 : t.getActiveIncidents());
                })
                .toList();
        String filter = role.trim().toUpperCase();
        model.addAttribute("counts", Map.of(
                "ALL", (long) rows.size(),
                "ADMIN", rows.stream().filter(r -> r.role().equals("ADMIN")).count(),
                "EMPLOYEE", rows.stream().filter(r -> r.role().equals("EMPLOYEE")).count(),
                "TECHNICIAN", rows.stream().filter(r -> r.role().equals("TECHNICIAN")).count()));
        // open incidents whose technician is unavailable or deactivated: the admin picks a new one
        model.addAttribute("stuck", incidents.findAll().stream()
                .filter(i -> !i.isFinished() && !i.isLinked())
                .filter(i -> {
                    if (i.getAssignedTechnician() == null) return true;   // waiting in the queue
                    Technician t = techs.get(i.getAssignedTechnician().getId());
                    return t != null && !t.isAvailable();
                })
                .map(i -> new StuckRow(i.getId(), i.getDescription(), i.getCategory(),
                        i.getAssignedTechnician() == null ? "— (in the queue)" : i.getAssignedTechnician().getName()))
                .toList());
        model.addAttribute("availableTechs", techs.values().stream()
                .filter(Technician::isAvailable)
                .sorted(Comparator.comparing(Technician::getName))
                .toList());
        model.addAttribute("roleFilter", filter);
        model.addAttribute("rows", filter.isEmpty() ? rows : rows.stream().filter(r -> r.role().equals(filter)).toList());
        model.addAttribute("categories", Category.values());
        return "admin/users";
    }

    @PostMapping("/users")
    public String create(@RequestParam("fullName") String fullName, @RequestParam("username") String username,
                         @RequestParam(name = "email", required = false) String email,
                         @RequestParam("password") String password, @RequestParam("role") String role,
                         @RequestParam(name = "skills", required = false) Set<String> skills,
                         RedirectAttributes redirect) {
        try {
            accounts.createByAdmin(fullName, username, email, password, role, skills);
            redirect.addFlashAttribute("message", "Account '" + username.trim().toLowerCase() + "' created as " + role.toLowerCase()
                    + ". Share the temporary password with them securely." + drainQueue());
        } catch (AccountException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{username}/enabled")
    public String setEnabled(@PathVariable("username") String username, @RequestParam("enabled") boolean enabled,
                             Authentication authentication, RedirectAttributes redirect) {
        try {
            accounts.setEnabled(username, enabled, authentication.getName());
            String msg = username + (enabled ? " reactivated." : " deactivated: they can no longer sign in.");
            boolean isTechnician = technicians.findAll().stream().anyMatch(t -> t.getId().equals(username));
            if (!enabled && isTechnician) {
                msg += " " + describe(workflow.reassignOpenIncidentsOf(username));
            }
            redirect.addFlashAttribute(msg.contains("Not moved") ? "error" : "message", msg);
        } catch (AccountException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /** Hand over the open incidents of an unavailable technician (e.g. on leave). */
    @PostMapping("/technicians/{id}/reassign-open")
    public String reassignOpen(@PathVariable("id") String id, RedirectAttributes redirect) {
        String msg = describe(workflow.reassignOpenIncidentsOf(id));
        redirect.addFlashAttribute(msg.contains("Not moved") ? "error" : "message", msg);
        return "redirect:/admin/users";
    }

    @PostMapping("/incidents/{id}/handover")
    public String handOver(@PathVariable("id") String id, @RequestParam("technicianId") String technicianId,
                           RedirectAttributes redirect) {
        try {
            Incident moved = workflow.handOver(id, technicianId);
            redirect.addFlashAttribute("message", id + " handed over to " + moved.getAssignedTechnician().getName() + ".");
        } catch (com.iitsm.iitsm.exception.InvalidIncidentDataException
                 | com.iitsm.iitsm.exception.TechnicianUnavailableException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /** After the admin frees capacity, queued incidents are assigned right away (not only by the job). */
    private String drainQueue() {
        int n = workflow.assignQueuedIncidents();
        return n == 0 ? "" : " " + n + " queued incident(s) were assigned.";
    }

    private static String describe(IncidentWorkflowService.HandoverResult r) {
        String text = r.moved() + " open incident(s) handed over to other technicians.";
        if (!r.notMoved().isEmpty()) {
            text += " Not moved, because no other available technician has the skill: " + String.join(", ", r.notMoved())
                    + ". Choose a technician for them below, under 'Waiting for a new technician'.";
        }
        return text;
    }

    @PostMapping("/technicians/{id}/skills")
    public String updateSkills(@PathVariable("id") String id,
                               @RequestParam(name = "skills", required = false) Set<String> skills,
                               RedirectAttributes redirect) {
        try {
            accounts.updateSkills(id, skills);
            redirect.addFlashAttribute("message", "Skills of " + id + " updated." + drainQueue());
        } catch (AccountException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/technicians/{id}/availability")
    public String setAvailability(@PathVariable("id") String id, @RequestParam("available") boolean available,
                                  RedirectAttributes redirect) {
        try {
            accounts.setAvailability(id, available);
            redirect.addFlashAttribute("message", id + (available ? " is available for new incidents." + drainQueue()
                    : " is unavailable: new incidents will go to others."));
        } catch (AccountException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
