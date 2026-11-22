package com.iitsm.iitsm.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An IT service managed by the admin (criticality 1-3, services it depends on). */
@Entity
@Table(name = "it_services")
public class ItServiceEntity {
    @Id
    @Column(length = 60)
    private String name;

    @Column(nullable = false)
    private int criticality;

    /** Comma-separated names of the services this one relies on. */
    @Column(length = 500)
    private String dependsOn;

    private Boolean active;

    protected ItServiceEntity() { }

    public ItServiceEntity(String name, int criticality, String dependsOn) {
        this.name = name;
        this.criticality = criticality;
        this.dependsOn = dependsOn;
        this.active = true;
    }

    public String getName() { return name; }
    public int getCriticality() { return criticality; }
    public String getDependsOn() { return dependsOn == null ? "" : dependsOn; }
    public boolean isActive() { return active == null || active; }

    public void setCriticality(int criticality) { this.criticality = criticality; }
    public void setDependsOn(String dependsOn) { this.dependsOn = dependsOn; }
    public void setActive(boolean active) { this.active = active; }
}
