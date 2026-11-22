package com.iitsm.iitsm.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.iitsm.iitsm.model.Priority;

/** An SLA that applies to one service and priority only, overriding the default for that priority. */
@Entity
@Table(name = "service_sla_policies", uniqueConstraints = @UniqueConstraint(columnNames = {"serviceName", "priority"}))
public class ServiceSlaPolicyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Priority priority;

    @Column(nullable = false)
    private long responseMinutes;

    @Column(nullable = false)
    private long resolutionMinutes;

    protected ServiceSlaPolicyEntity() { }

    public ServiceSlaPolicyEntity(String serviceName, Priority priority, long responseMinutes, long resolutionMinutes) {
        this.serviceName = serviceName;
        this.priority = priority;
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
    }

    public Long getId() { return id; }
    public String getServiceName() { return serviceName; }
    public Priority getPriority() { return priority; }
    public long getResponseMinutes() { return responseMinutes; }
    public long getResolutionMinutes() { return resolutionMinutes; }

    public void setTargets(long responseMinutes, long resolutionMinutes) {
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
    }
}
