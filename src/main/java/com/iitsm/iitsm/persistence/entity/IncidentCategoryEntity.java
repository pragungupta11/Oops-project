package com.iitsm.iitsm.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An incident category managed by the admin, with keywords for the category suggester. */
@Entity
@Table(name = "incident_categories")
public class IncidentCategoryEntity {
    @Id
    @Column(length = 40)
    private String name;

    /** Comma-separated keywords, e.g. "printer, toner, paper jam". */
    @Column(length = 1000)
    private String keywords;

    private Boolean active;

    private boolean builtIn;

    protected IncidentCategoryEntity() { }

    public IncidentCategoryEntity(String name, String keywords, boolean builtIn) {
        this.name = name;
        this.keywords = keywords;
        this.active = true;
        this.builtIn = builtIn;
    }

    public String getName() { return name; }
    public String getKeywords() { return keywords == null ? "" : keywords; }
    public boolean isActive() { return active == null || active; }
    public boolean isBuiltIn() { return builtIn; }

    public void setKeywords(String keywords) { this.keywords = keywords; }
    public void setActive(boolean active) { this.active = active; }
}
