package com.iitsm.iitsm.web;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.service.SlaService;

/**
 * A page-ready copy of an Incident: everything is already text, so templates stay simple.
 * The *Class fields are Bootstrap badge classes.
 */
public record IncidentView(String id, String description, String category, String serviceName,
                           String priority, String priorityClass,
                           String status, String statusClass,
                           String technicianName, String technicianId, String reporter,
                           String created, String responseDue, String resolutionDue,
                           String sla, String slaClass, String resolution) {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    public static IncidentView of(Incident i, SlaService slaService) {
        Priority p = i.getPriority();
        IncidentStatus s = i.getStatus();
        boolean open = s != IncidentStatus.RESOLVED && s != IncidentStatus.VERIFIED
                && s != IncidentStatus.CLOSED;

        String sla = "-";
        if (p != null && open) {
            sla = slaService.checkResolution(i.getCreatedAt(), p, LocalDateTime.now()).name();
        }
        String responseDue = p == null ? "-" : FORMAT.format(slaService.responseDeadline(i.getCreatedAt(), p));
        String resolutionDue = p == null ? "-" : FORMAT.format(slaService.resolutionDeadline(i.getCreatedAt(), p));

        return new IncidentView(
                i.getId(), i.getDescription(), i.getCategory(),
                orDash(i.getServiceName()),
                p == null ? "-" : p.name(), priorityClass(p),
                s.name(), statusClass(s),
                i.getAssignedTechnician() == null ? "-" : i.getAssignedTechnician().getName(),
                i.getAssignedTechnician() == null ? "" : i.getAssignedTechnician().getId(),
                orDash(i.getReporterUsername()),
                FORMAT.format(i.getCreatedAt()), responseDue, resolutionDue,
                sla, slaClass(sla),
                orDash(i.getResolution()));
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String priorityClass(Priority p) {
        if (p == null) return "text-bg-secondary";
        return switch (p) {
            case P1 -> "text-bg-danger";
            case P2 -> "text-bg-warning";
            case P3 -> "text-bg-info";
            case P4 -> "text-bg-secondary";
        };
    }

    private static String statusClass(IncidentStatus s) {
        return switch (s) {
            case NEW, CLASSIFIED, ASSIGNED -> "text-bg-secondary";
            case ACKNOWLEDGED, IN_PROGRESS -> "text-bg-primary";
            case RESOLVED, VERIFIED -> "text-bg-success";
            case CLOSED -> "text-bg-dark";
        };
    }

    private static String slaClass(String sla) {
        return switch (sla) {
            case "OK" -> "text-bg-success";
            case "AT_RISK" -> "text-bg-warning";
            case "VIOLATED" -> "text-bg-danger";
            default -> "text-bg-secondary";
        };
    }
}
