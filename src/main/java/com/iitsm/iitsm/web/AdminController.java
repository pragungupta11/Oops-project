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
import com.iitsm.iitsm.service.PriorityStrategyRegistry;
import com.iitsm.iitsm.service.SlaService;
import com.iitsm.iitsm.exception.InvalidSlaConfigurationException;
import com.iitsm.iitsm.persistence.entity.AuditLogEntity;
import com.iitsm.iitsm.repository.AuditLogRepository;
import com.iitsm.iitsm.service.CategoryCatalog;
import com.iitsm.iitsm.service.SlaConfigurationService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

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
    private final PriorityStrategyRegistry priorityStrategies;
    private final UiLabels labels;
    private final ServiceCatalog serviceCatalog;
    private final CategoryCatalog categoryCatalog;
    private final IncidentConversationService conversation;
    private final AuditLogRepository auditLogs;
    private final SlaConfigurationService slaConfig;

    public AdminController(IncidentPersistenceService incidents, TechnicianPersistenceService technicians,
                           SlaService slaService, IncidentWorkflowService workflow,
                           AssignmentStrategyRegistry strategies,
                           UiLabels labels,
                           SlaConfigurationService slaConfig,
                           AuditLogRepository auditLogs,
                           IncidentConversationService conversation,
                           ServiceCatalog serviceCatalog,
                           CategoryCatalog categoryCatalog,
                           PriorityStrategyRegistry priorityStrategies) {
        this.incidents = incidents;
        this.technicians = technicians;
        this.slaService = slaService;
        this.workflow = workflow;
        this.strategies = strategies;
        this.labels = labels;
        this.slaConfig = slaConfig;
        this.auditLogs = auditLogs;
        this.conversation = conversation;
        this.serviceCatalog = serviceCatalog;
        this.categoryCatalog = categoryCatalog;
        this.priorityStrategies = priorityStrategies;
    }

    /** One bar on the dashboard. */
    public record Bar(String label, int count, int percent) {}

    /** One row of the service analysis report. */
    public record ServiceRow(String name, int criticality, int total, int open, int violated) {}

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
                       @RequestParam(name = "service", defaultValue = "") String service,
                       @RequestParam(name = "from", defaultValue = "") String from,
                       @RequestParam(name = "to", defaultValue = "") String to,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       Model model) {
        String kw = keyword.trim();
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        List<IncidentView> rows = incidents.findAll().stream()
                .filter(i -> service.isBlank() || service.equals(i.getServiceName()))
                .filter(i -> fromDate == null || !i.getCreatedAt().toLocalDate().isBefore(fromDate))
                .filter(i -> toDate == null || !i.getCreatedAt().toLocalDate().isAfter(toDate))
                .sorted(Comparator.comparing(Incident::getCreatedAt).reversed())
                .map(i -> IncidentView.of(i, slaService))
                .filter(v -> status.isBlank() || v.status().equals(status))
                .filter(v -> priority.isBlank() || v.priority().equals(priority))
                .filter(v -> category.isBlank() || category.equalsIgnoreCase(v.category()))
                .filter(v -> technicianId.isBlank() || v.technicianId().equals(technicianId))
                .filter(v -> slaStatus.isBlank() || v.sla().equals(slaStatus))
                .filter(v -> kw.isEmpty() || containsIgnoreCase(v.description(), kw) || containsIgnoreCase(v.id(), kw))
                .toList();

        // 50 per page, newest first
        int pages = Math.max(1, (rows.size() + INCIDENT_PAGE_SIZE - 1) / INCIDENT_PAGE_SIZE);
        int current = Math.min(Math.max(0, page), pages - 1);
        model.addAttribute("total", rows.size());
        model.addAttribute("page", current);
        model.addAttribute("pages", pages);
        rows = rows.subList(current * INCIDENT_PAGE_SIZE, Math.min(rows.size(), (current + 1) * INCIDENT_PAGE_SIZE));

        model.addAttribute("rows", rows);
        model.addAttribute("statuses", IncidentStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("categories", categoryCatalog.allNames());
        model.addAttribute("technicians", technicians.findAll());
        model.addAttribute("slaOptions", SLA_OPTIONS);
        model.addAttribute("selStatus", status);
        model.addAttribute("selPriority", priority);
        model.addAttribute("selCategory", category);
        model.addAttribute("selTech", technicianId);
        model.addAttribute("selSla", slaStatus);
        model.addAttribute("services", serviceCatalog.allIncludingInactive());
        model.addAttribute("selService", service);
        model.addAttribute("selFrom", fromDate == null ? "" : fromDate.toString());
        model.addAttribute("selTo", toDate == null ? "" : toDate.toString());
        model.addAttribute("keyword", keyword);

        List<Technician> allTechs = technicians.findAll();
        List<ReroutingView> rerouting = incidents.findWithPendingReassignment().stream()
                .map(i -> new ReroutingView(i.getId(), i.getDescription(), i.getCategory(),
                        i.getRequestedCategory(), i.getReassignmentReason(),
                        i.getAssignedTechnician() == null ? "-" : i.getAssignedTechnician().getName(),
                        allTechs.stream().filter(t -> t.getSkills().contains(i.getRequestedCategory())).toList()))
                .toList();
        model.addAttribute("rerouting", rerouting);
        return "admin/incidents";
    }

    /** Change the priority of an open incident (reason required); notifies technician and employee. */
    @PostMapping("/incidents/{id}/priority")
    public String changePriority(@PathVariable("id") String id, @RequestParam("priority") String priority,
                                 @RequestParam(name = "reason", defaultValue = "") String reason,
                                 RedirectAttributes redirect) {
        try {
            Incident updated = workflow.changePriority(id, Priority.valueOf(priority), reason);
            redirect.addFlashAttribute("message", id + " is now " + updated.getPriority() + ".");
        } catch (InvalidIncidentDataException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "Unknown incident or priority");
        }
        return "redirect:/admin/incidents";
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
    public record AuditRow(String time, String actor, String action, String entity, String details, String change) {}

    private static final DateTimeFormatter AUDIT_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", Locale.ENGLISH)
                    .withZone(ZoneId.systemDefault());

    static final int AUDIT_PAGE_SIZE = 100;
    static final int INCIDENT_PAGE_SIZE = 50;

    /**
     * Every recorded action, newest first, 100 per page; optional search over all columns.
     * Paging and searching happen in the database, so the page stays fast as the log grows.
     */
    @GetMapping("/audit")
    public String auditLog(@RequestParam(name = "q", defaultValue = "") String q,
                           @RequestParam(name = "page", defaultValue = "0") int page, Model model) {
        String query = q.trim();
        Pageable pageable =
                PageRequest.of(Math.max(0, page), AUDIT_PAGE_SIZE);
        Slice<AuditLogEntity> slice = query.isEmpty()
                ? auditLogs.findAllByOrderByOccurredAtDesc(pageable)
                : auditLogs.search("%" + query.toLowerCase() + "%", pageable);
        List<AuditRow> rows = slice.getContent().stream()
                .map(a -> new AuditRow(AUDIT_TIME.format(a.getOccurredAt()), a.getActor(), a.getAction(),
                        a.getEntityType() + " " + a.getEntityId(), a.getDetails(),
                        a.getNewState() == null ? "" : (a.getPreviousState() == null ? "(new)" : a.getPreviousState())
                                + " → " + a.getNewState()))
                .toList();
        model.addAttribute("rows", rows);
        model.addAttribute("q", q);
        model.addAttribute("page", Math.max(0, page));
        model.addAttribute("hasNext", slice.hasNext());
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
        model.addAttribute("overrides", slaConfig.serviceOverrides());
        model.addAttribute("services", serviceCatalog.allIncludingInactive());
        model.addAttribute("priorities", Priority.values());
        return "admin/sla";
    }

    @PostMapping("/sla/service")
    public String saveServiceSla(@RequestParam("service") String service, @RequestParam("priority") String priority,
                                 @RequestParam("responseMinutes") long responseMinutes,
                                 @RequestParam("resolutionMinutes") long resolutionMinutes,
                                 RedirectAttributes redirect) {
        try {
            slaConfig.setServiceOverride(service, Priority.valueOf(priority), responseMinutes, resolutionMinutes);
            redirect.addFlashAttribute("message", service + " " + priority + ": respond within " + responseMinutes
                    + " min, resolve within " + resolutionMinutes + " min.");
        } catch (InvalidSlaConfigurationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "Unknown priority: " + priority);
        }
        return "redirect:/admin/sla";
    }

    @PostMapping("/sla/service/remove")
    public String removeServiceSla(@RequestParam("service") String service, @RequestParam("priority") String priority,
                                   RedirectAttributes redirect) {
        try {
            slaConfig.removeServiceOverride(service, Priority.valueOf(priority));
            redirect.addFlashAttribute("message", service + " " + priority + " uses the default SLA again.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "Unknown priority: " + priority);
        }
        return "redirect:/admin/sla";
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
        } catch (InvalidSlaConfigurationException e) {
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
        Map<String, Long> perCategory = new LinkedHashMap<>();
        for (String c : categoryCatalog.allNames()) {
            perCategory.put(c, 0L);   // show every category, also the quiet ones
        }
        all.forEach(v -> perCategory.merge(v.category(), 1L, Long::sum));
        model.addAttribute("categoryLabels", new ArrayList<>(perCategory.keySet()));
        model.addAttribute("categoryCounts", new ArrayList<>(perCategory.values()));
        Set<String> reopened = auditLogs.entityIdsWithAction("INCIDENT_REOPENED");
        model.addAttribute("reopened", reopened);
        model.addAttribute("attention", all.stream()
                .filter(v -> v.open() && (v.sla().equals("VIOLATED") || v.sla().equals("AT_RISK")
                        || !v.escalation().isEmpty() || reopened.contains(v.id())
                        || (v.technicianId().isEmpty() && !v.linked())))
                .sorted(Comparator.comparing((IncidentView v) -> attentionRank(v, reopened)))
                .limit(8)
                .toList());
        model.addAttribute("avgRating", conversation.averageRating()
                .map(r -> String.format(Locale.ENGLISH, "%.1f", r)).orElse("-"));
        model.addAttribute("ratingCount", conversation.feedbackCount());
        // Service analysis: incidents per IT service (all time, open, SLA violations)
        model.addAttribute("serviceRows", serviceCatalog.allIncludingInactive().stream()
                .map(svc -> new ServiceRow(svc.getName(), svc.getCriticality(),
                        count(all, v -> svc.getName().equals(v.serviceName())),
                        count(all, v -> svc.getName().equals(v.serviceName()) && v.open()),
                        count(all, v -> svc.getName().equals(v.serviceName()) && v.sla().equals("VIOLATED"))))
                .sorted(Comparator.comparing(ServiceRow::total).reversed())
                .toList());
        model.addAttribute("refreshedAt", LocalTime.now().withNano(0).toString());

        model.addAttribute("total", total);
        // "Open" counts problems: a report linked to another incident is the same problem, shown separately
        model.addAttribute("open", count(all, v -> v.open() && !v.linked()));
        model.addAttribute("openLinked", count(all, v -> v.open() && v.linked()));
        model.addAttribute("atRisk", count(all, v -> v.sla().equals("AT_RISK")));
        model.addAttribute("violated", count(all, v -> v.sla().equals("VIOLATED")));
        model.addAttribute("awaitingVerify", count(all, v -> v.status().equals("RESOLVED")));
        model.addAttribute("byStatus", byStatus);
        model.addAttribute("byPriority", byPriority);
        model.addAttribute("techs", techs);
        model.addAttribute("strategyNames", new TreeSet<>(strategies.availableNames()));
        model.addAttribute("activeStrategy", strategies.currentName());
        model.addAttribute("priorityStrategyNames", new TreeSet<>(priorityStrategies.availableNames()));
        model.addAttribute("activePriorityStrategy", priorityStrategies.currentName());
        return "admin/dashboard";
    }

    /** Lower = more urgent: violated, escalated, reopened by the employee, then at risk. */
    private static int attentionRank(IncidentView v, Set<String> reopened) {
        if (v.sla().equals("VIOLATED")) return 0;
        if (!v.escalation().isEmpty() || v.technicianId().isEmpty()) return 1;   // escalated or unassigned
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

    /** Switch how the priority of NEW incidents is calculated; existing incidents keep theirs. */
    @PostMapping("/priority-strategy")
    public String switchPriorityStrategy(@RequestParam("name") String name, RedirectAttributes redirect) {
        try {
            priorityStrategies.switchTo(name);
            redirect.addFlashAttribute("message", "New incidents will now be prioritised with: "
                    + labels.priorityStrategy(name) + ". Existing incidents keep their priority.");
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

    /** yyyy-MM-dd from a date input, or null when empty or invalid. */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean containsIgnoreCase(String text, String part) {
        return text != null && text.toLowerCase().contains(part.toLowerCase());
    }
}
