package com.iitsm.iitsm.web;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Predicate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.iitsm.model.Category;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.Technician;
import com.iitsm.iitsm.persistence.service.IncidentPersistenceService;
import com.iitsm.iitsm.persistence.service.TechnicianPersistenceService;
import com.iitsm.iitsm.service.AssignmentStrategyRegistry;
import com.iitsm.iitsm.service.SlaService;

/** Admin pages: all incidents with filters, verify/close, and the dashboard. */
@Controller
@RequestMapping("/admin")
public class AdminController {
    private static final List<String> SLA_OPTIONS = List.of("OK", "AT_RISK", "VIOLATED", "MET");

    private final IncidentPersistenceService incidents;
    private final TechnicianPersistenceService technicians;
    private final SlaService slaService;
    private final IncidentWorkflowService workflow;
    private final AssignmentStrategyRegistry strategies;
    private final UiLabels labels;
    private final IncidentConversationService conversation;
    private final com.iitsm.iitsm.repository.AuditLogRepository auditLogs;
    private final com.iitsm.iitsm.service.SlaConfigurationService slaConfig;

    public AdminController(IncidentPersistenceService incidents, TechnicianPersistenceService technicians,
                           SlaService slaService, IncidentWorkflowService workflow,
                           AssignmentStrategyRegistry strategies,
                           UiLabels labels,
                           com.iitsm.iitsm.service.SlaConfigurationService slaConfig,
                           com.iitsm.iitsm.repository.AuditLogRepository auditLogs,
                           IncidentConversationService conversation) {
        this.incidents = incidents;
        this.technicians = technicians;
        this.slaService = slaService;
        this.workflow = workflow;
        this.strategies = strategies;
        this.labels = labels;
        this.slaConfig = slaConfig;
        this.auditLogs = auditLogs;
        this.conversation = conversation;
    }

    /** One bar on the dashboard. */
    public record Bar(String label, int count, int percent) {}

    /** One pending re-routing request shown to the admin. */
    public record ReroutingView(String id, String description, String currentCategory,
                                String requestedCategory, String reason, String technicianName,
                                List<Technician> skilledTechnicians) {}

    /** One row of the technician workload table. */
    public record TechView(String id, String name, String skills, int active, String available) {}

    // ---------------------------------------------------------------- incident list

    @GetMapping("/incidents")
    public String list(@RequestParam(name = "status", defaultValue = "") String status,
                       @RequestParam(name = "priority", defaultValue = "") String priority,
                       @RequestParam(name = "category", defaultValue = "") String category,
                       @RequestParam(name = "technicianId", defaultValue = "") String technicianId,
                       @RequestParam(name = "slaStatus", defaultValue = "") String slaStatus,
                       @RequestParam(name = "keyword", defaultValue = "") String keyword,
                       Model model) {
        String kw = keyword.trim();
        List<IncidentView> rows = incidents.findAll().stream()
                .sorted(Comparator.comparing(Incident::getCreatedAt).reversed())
                .map(i -> IncidentView.of(i, slaService))
                .filter(v -> status.isBlank() || v.status().equals(status))
                .filter(v -> priority.isBlank() || v.priority().equals(priority))
                .filter(v -> category.isBlank() || category.equalsIgnoreCase(v.category()))
                .filter(v -> technicianId.isBlank() || v.technicianId().equals(technicianId))
                .filter(v -> slaStatus.isBlank() || v.sla().equals(slaStatus))
                .filter(v -> kw.isEmpty() || containsIgnoreCase(v.description(), kw) || containsIgnoreCase(v.id(), kw))
                .toList();

        model.addAttribute("rows", rows);
        model.addAttribute("statuses", IncidentStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("categories", Category.values());
        model.addAttribute("technicians", technicians.findAll());
        model.addAttribute("slaOptions", SLA_OPTIONS);
        model.addAttribute("selStatus", status);
        model.addAttribute("selPriority", priority);
        model.addAttribute("selCategory", category);
        model.addAttribute("selTech", technicianId);
        model.addAttribute("selSla", slaStatus);
        model.addAttribute("keyword", keyword);

        List<Technician> allTechs = technicians.findAll();
        List<ReroutingView> rerouting = incidents.findAll().stream()
                .filter(Incident::hasPendingReassignment)
                .map(i -> new ReroutingView(i.getId(), i.getDescription(), i.getCategory(),
                        i.getRequestedCategory(), i.getReassignmentReason(),
                        i.getAssignedTechnician() == null ? "-" : i.getAssignedTechnician().getName(),
                        allTechs.stream().filter(t -> t.getSkills().contains(i.getRequestedCategory())).toList()))
                .toList();
        model.addAttribute("rerouting", rerouting);
        return "admin/incidents";
    }

    /** Exception only: close a resolved incident without waiting for the employee (reason required). */
    @PostMapping("/incidents/{id}/close-now")
    public String closeNow(@PathVariable("id") String id, @RequestParam("reason") String reason,
                           RedirectAttributes redirect) {
        try {
            workflow.adminClose(id, reason);
            redirect.addFlashAttribute("message", id + " closed by admin override.");
        } catch (InvalidStateTransitionException | InvalidIncidentDataException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/incidents";
    }

    @PostMapping("/incidents/{id}/reassign/approve")
    public String approveReassignment(@PathVariable("id") String id,
                                      @RequestParam(name = "technicianId", defaultValue = "auto") String technicianId,
                                      RedirectAttributes redirect) {
        try {
            Incident updated = workflow.approveReassignment(id, technicianId);
            redirect.addFlashAttribute("message", id + " re-routed to " + updated.getCategory()
                    + " and assigned to " + updated.getAssignedTechnician().getName() + ".");
        } catch (InvalidIncidentDataException | InvalidStateTransitionException
                 | TechnicianUnavailableException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/incidents";
    }

    @PostMapping("/incidents/{id}/unlink")
    public String unlink(@PathVariable("id") String id, RedirectAttributes redirect) {
        try {
            Incident unlinked = workflow.unlink(id, null);
            redirect.addFlashAttribute("message", id + " is a separate incident again, assigned to "
                    + unlinked.getAssignedTechnician().getName() + ".");
        } catch (InvalidIncidentDataException | InvalidStateTransitionException
                 | TechnicianUnavailableException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/incidents";
    }

    @PostMapping("/incidents/{id}/reassign/reject")
    public String rejectReassignment(@PathVariable("id") String id, RedirectAttributes redirect) {
        try {
            workflow.rejectReassignment(id);
            redirect.addFlashAttribute("message", "Re-routing of " + id + " rejected; it stays with the current technician.");
        } catch (InvalidIncidentDataException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/incidents";
    }

    // ---------------------------------------------------------------- audit log

    /** One row of the audit log page. */
    public record AuditRow(String time, String actor, String action, String entity, String details) {}

    private static final java.time.format.DateTimeFormatter AUDIT_TIME =
            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", java.util.Locale.ENGLISH)
                    .withZone(java.time.ZoneId.systemDefault());

    /** Every recorded action, newest first; optional search over all columns. Shows the latest 300. */
    @GetMapping("/audit")
    public String auditLog(@RequestParam(name = "q", defaultValue = "") String q, Model model) {
        String query = q.trim();
        List<AuditRow> rows = auditLogs.findAllByOrderByOccurredAtDesc().stream()
                .map(a -> new AuditRow(AUDIT_TIME.format(a.getOccurredAt()), a.getActor(), a.getAction(),
                        a.getEntityType() + " " + a.getEntityId(), a.getDetails()))
                .filter(r -> query.isEmpty() || containsIgnoreCase(r.actor(), query) || containsIgnoreCase(r.action(), query)
                        || containsIgnoreCase(r.entity(), query) || containsIgnoreCase(r.details(), query))
                .limit(300)
                .toList();
        model.addAttribute("rows", rows);
        model.addAttribute("q", q);
        return "admin/audit";
    }

    // ---------------------------------------------------------------- SLA policies

    /** One row of the SLA policy table. */
    public record SlaRow(String priority, String urgency, long responseMinutes, long resolutionMinutes) {}

    @GetMapping("/sla")
    public String slaPolicies(Model model) {
        List<SlaRow> rows = slaConfig.current().entrySet().stream()
                .map(e -> new SlaRow(e.getKey().name(), urgencyWord(e.getKey()),
                        e.getValue().responseTarget().toMinutes(), e.getValue().resolutionTarget().toMinutes()))
                .toList();
        model.addAttribute("rows", rows);
        return "admin/sla";
    }

    @PostMapping("/sla/{priority}")
    public String saveSlaPolicy(@PathVariable("priority") String priority,
                                @RequestParam("responseMinutes") long responseMinutes,
                                @RequestParam("resolutionMinutes") long resolutionMinutes,
                                RedirectAttributes redirect) {
        try {
            Priority p = Priority.valueOf(priority);
            slaConfig.update(p, responseMinutes, resolutionMinutes);
            redirect.addFlashAttribute("message", p + " now: respond within " + responseMinutes
                    + " min, resolve within " + resolutionMinutes + " min.");
        } catch (com.iitsm.iitsm.exception.InvalidSlaConfigurationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "Unknown priority: " + priority);
        }
        return "redirect:/admin/sla";
    }

    private static String urgencyWord(Priority p) {
        return switch (p) {
            case P1 -> "Critical";
            case P2 -> "High";
            case P3 -> "Medium";
            case P4 -> "Low";
        };
    }

    // ---------------------------------------------------------------- dashboard

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<IncidentView> all = incidents.findAll().stream()
                .map(i -> IncidentView.of(i, slaService))
                .toList();
        int total = all.size();

        List<Bar> byStatus = Arrays.stream(IncidentStatus.values())
                .map(s -> bar(s.name(), count(all, v -> v.status().equals(s.name())), total))
                .toList();
        List<Bar> byPriority = Arrays.stream(Priority.values())
                .map(p -> bar(p.name(), count(all, v -> v.priority().equals(p.name())), total))
                .toList();

        int resolvedOrDone = count(all, v -> v.status().equals("RESOLVED")
                || v.status().equals("VERIFIED") || v.status().equals("CLOSED"));

        List<TechView> techs = technicians.findAll().stream()
                .sorted(Comparator.comparing(Technician::getName))
                .map(t -> new TechView(t.getId(), t.getName(),
                        String.join(", ", new TreeSet<>(t.getSkills())),
                        t.getActiveIncidents(), t.isAvailable() ? "Yes" : "No"))
                .toList();

        // charts (labels in plain words) and the "needs attention" list
        model.addAttribute("statusLabels", byStatus.stream().map(b -> labels.status(b.label())).toList());
        model.addAttribute("statusCounts", byStatus.stream().map(Bar::count).toList());
        java.util.Map<String, Long> perCategory = new java.util.LinkedHashMap<>();
        for (com.iitsm.iitsm.model.Category c : com.iitsm.iitsm.model.Category.values()) {
            perCategory.put(c.label(), 0L);   // show every category, also the quiet ones
        }
        all.forEach(v -> perCategory.merge(v.category(), 1L, Long::sum));
        model.addAttribute("categoryLabels", new java.util.ArrayList<>(perCategory.keySet()));
        model.addAttribute("categoryCounts", new java.util.ArrayList<>(perCategory.values()));
        java.util.Set<String> reopened = auditLogs.findAll().stream()
                .filter(a -> "INCIDENT_REOPENED".equals(a.getAction()))
                .map(a -> a.getEntityId())
                .collect(java.util.stream.Collectors.toSet());
        model.addAttribute("reopened", reopened);
        model.addAttribute("attention", all.stream()
                .filter(v -> v.open() && (v.sla().equals("VIOLATED") || v.sla().equals("AT_RISK")
                        || !v.escalation().isEmpty() || reopened.contains(v.id())))
                .sorted(Comparator.comparing((IncidentView v) -> attentionRank(v, reopened)))
                .limit(8)
                .toList());
        model.addAttribute("avgRating", conversation.averageRating()
                .map(r -> String.format(java.util.Locale.ENGLISH, "%.1f", r)).orElse("-"));
        model.addAttribute("ratingCount", conversation.feedbackCount());
        model.addAttribute("refreshedAt", java.time.LocalTime.now().withNano(0).toString());

        model.addAttribute("total", total);
        model.addAttribute("open", total - resolvedOrDone);
        model.addAttribute("atRisk", count(all, v -> v.sla().equals("AT_RISK")));
        model.addAttribute("violated", count(all, v -> v.sla().equals("VIOLATED")));
        model.addAttribute("awaitingVerify", count(all, v -> v.status().equals("RESOLVED")));
        model.addAttribute("byStatus", byStatus);
        model.addAttribute("byPriority", byPriority);
        model.addAttribute("techs", techs);
        model.addAttribute("strategyNames", new TreeSet<>(strategies.availableNames()));
        model.addAttribute("activeStrategy", strategies.currentName());
        return "admin/dashboard";
    }

    /** Lower = more urgent: violated, escalated, reopened by the employee, then at risk. */
    private static int attentionRank(IncidentView v, java.util.Set<String> reopened) {
        if (v.sla().equals("VIOLATED")) return 0;
        if (!v.escalation().isEmpty()) return 1;
        if (reopened.contains(v.id())) return 2;
        return 3;   // at risk
    }

    @PostMapping("/strategy")
    public String switchStrategy(@RequestParam("name") String name, RedirectAttributes redirect) {
        try {
            strategies.switchTo(name);
            redirect.addFlashAttribute("message", "New incidents will now be assigned using: " + labels.strategy(name));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

    // ---------------------------------------------------------------- helpers

    private static int count(List<IncidentView> views, Predicate<IncidentView> test) {
        return (int) views.stream().filter(test).count();
    }

    private static Bar bar(String label, int count, int total) {
        int percent = total == 0 ? 0 : Math.round(count * 100f / total);
        return new Bar(label, count, percent);
    }

    private static boolean containsIgnoreCase(String text, String part) {
        return text != null && text.toLowerCase().contains(part.toLowerCase());
    }
}
