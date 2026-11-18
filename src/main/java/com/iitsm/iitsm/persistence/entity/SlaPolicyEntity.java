package com.iitsm.iitsm.persistence.entity;

import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.SlaPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;

@Entity
@Table(name = "sla_policies")
public class SlaPolicyEntity {
    @Id
    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Column(nullable = false)
    private long responseTargetSeconds;

    @Column(nullable = false)
    private long resolutionTargetSeconds;

    protected SlaPolicyEntity() { }

    public SlaPolicyEntity(Priority priority, long responseTargetSeconds, long resolutionTargetSeconds) {
        this.priority = priority;
        this.responseTargetSeconds = responseTargetSeconds;
        this.resolutionTargetSeconds = resolutionTargetSeconds;
    }

    public static SlaPolicyEntity fromDomain(Priority priority, SlaPolicy policy) {
        return new SlaPolicyEntity(priority, policy.responseTarget().toSeconds(), policy.resolutionTarget().toSeconds());
    }

    public SlaPolicy toDomain() {
        return new SlaPolicy(Duration.ofSeconds(responseTargetSeconds), Duration.ofSeconds(resolutionTargetSeconds));
    }

    public Priority getPriority() { return priority; }
}
