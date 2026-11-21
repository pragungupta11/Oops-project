package com.iitsm.iitsm.web;

import org.springframework.stereotype.Component;

/**
 * Turns internal codes into words people read, e.g. IN_PROGRESS -> "In progress".
 * Used from templates as ${@labels.status(...)}.
 */
@Component("labels")
public class UiLabels {

    public String status(String code) {
        if (code == null) return "-";
        return switch (code) {
            case "NEW" -> "New";
            case "CLASSIFIED" -> "Classified";
            case "ASSIGNED" -> "Assigned";
            case "ACKNOWLEDGED" -> "Acknowledged";
            case "IN_PROGRESS" -> "In progress";
            case "RESOLVED" -> "Resolved";
            case "VERIFIED" -> "Verified";
            case "CLOSED" -> "Closed";
            default -> code;
        };
    }

    public String sla(String code) {
        if (code == null) return "-";
        return switch (code) {
            case "OK" -> "On track";
            case "AT_RISK" -> "At risk";
            case "VIOLATED" -> "Violated";
            default -> code;
        };
    }

    public String strategy(String name) {
        if (name == null) return "-";
        return switch (name) {
            case "skillBased" -> "Skill-based";
            case "loadBased" -> "Load-based";
            default -> name;
        };
    }

    public String escalation(String code) {
        if (code == null || code.isEmpty()) return "";
        return switch (code) {
            case "TECHNICIAN" -> "Technician";
            case "TEAM_LEAD" -> "Team lead";
            case "SYSTEM_ADMIN" -> "System admin";
            case "IT_MANAGER" -> "IT manager";
            default -> code;
        };
    }

    /** Bootstrap Icons name for a status. */
    public String statusIcon(String code) {
        if (code == null) return "circle";
        return switch (code) {
            case "NEW", "CLASSIFIED" -> "inbox";
            case "ASSIGNED" -> "person-check";
            case "ACKNOWLEDGED" -> "eye";
            case "IN_PROGRESS" -> "tools";
            case "RESOLVED" -> "check-circle";
            case "VERIFIED" -> "patch-check";
            case "CLOSED" -> "archive";
            default -> "circle";
        };
    }
}
