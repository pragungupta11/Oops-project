package com.iitsm.iitsm.model;

import java.util.Set;

public class Technician extends User {
    private final Set<String> skills;
    private int activeIncidents;
    private boolean available;

    public Technician(String id, String name, Set<String> skills,
                      int activeIncidents, boolean available) {
        this(id, name, name.toLowerCase().replace(" ", ".") + "@company.com",
             skills, activeIncidents, available);
    }

    public Technician(String id, String name, String email, Set<String> skills,
                      int activeIncidents, boolean available) {
        super(id, name, email);
        this.skills = skills;
        this.activeIncidents = activeIncidents;
        this.available = available;
    }

    public Set<String> getSkills() { return skills; }
    public int getActiveIncidents() { return activeIncidents; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public void incrementActiveIncidents() { activeIncidents++; }
    public void decrementActiveIncidents() { if (activeIncidents > 0) activeIncidents--; }

    @Override public Role getRole() { return Role.TECHNICIAN; }
    @Override public boolean canViewAllIncidents() { return false; }
}