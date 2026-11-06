package com.iitsm.iitsm.model;

import java.util.Set;

public class Technician {
    private final String id;
    private final String name;
    private final Set<String> skills;
    private int activeIncidents;
    private boolean available;

    public Technician(String id, String name, Set<String> skills,
                      int activeIncidents, boolean available) {
        this.id = id;
        this.name = name;
        this.skills = skills;
        this.activeIncidents = activeIncidents;
        this.available = available;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Set<String> getSkills() { return skills; }
    public int getActiveIncidents() { return activeIncidents; }
    public boolean isAvailable() { return available; }
}
