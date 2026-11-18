package com.iitsm.iitsm.persistence.entity;

import com.iitsm.iitsm.model.Technician;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "technicians")
public class TechnicianEntity {
    @Id
    @Column(length = 80)
    private String id;

    @Column(nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "technician_skills", joinColumns = @JoinColumn(name = "technician_id"))
    @Column(name = "skill", nullable = false)
    private Set<String> skills = new HashSet<>();

    @Column(nullable = false)
    private int activeIncidents;

    @Column(nullable = false)
    private boolean available;

    protected TechnicianEntity() { }

    public TechnicianEntity(String id, String name, Set<String> skills,
                            int activeIncidents, boolean available) {
        this.id = id;
        this.name = name;
        this.skills = skills == null ? new HashSet<>() : new HashSet<>(skills);
        this.activeIncidents = activeIncidents;
        this.available = available;
    }

    public static TechnicianEntity fromDomain(Technician technician) {
        return new TechnicianEntity(technician.getId(), technician.getName(),
                technician.getSkills(), technician.getActiveIncidents(), technician.isAvailable());
    }

    public Technician toDomain() {
        return new Technician(id, name, new HashSet<>(skills), activeIncidents, available);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Set<String> getSkills() { return new HashSet<>(skills); }
    public int getActiveIncidents() { return activeIncidents; }
    public boolean isAvailable() { return available; }
}
