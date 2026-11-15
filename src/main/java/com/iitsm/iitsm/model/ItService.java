package com.iitsm.iitsm.model;

import java.util.Set;

/** An IT service that incidents can affect, e.g. Payment Gateway. */
public class ItService {
    private final String name;
    private final int criticality;          // 1 to 3
    private final Set<String> dependsOn;    // names of services this one relies on

    public ItService(String name, int criticality, Set<String> dependsOn) {
        this.name = name;
        this.criticality = criticality;
        this.dependsOn = dependsOn;
    }

    public String getName() { return name; }
    public int getCriticality() { return criticality; }
    public Set<String> getDependsOn() { return dependsOn; }
}