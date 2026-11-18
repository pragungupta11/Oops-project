package com.iitsm.iitsm.persistence.service;

import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.SlaPolicy;
import com.iitsm.iitsm.persistence.entity.SlaPolicyEntity;
import com.iitsm.iitsm.repository.SlaPolicyRepository;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SlaPolicyInitializer implements ApplicationRunner {
    private final SlaPolicyRepository repository;

    public SlaPolicyInitializer(SlaPolicyRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<Priority, SlaPolicy> defaults = new EnumMap<>(Priority.class);
        defaults.put(Priority.P1, new SlaPolicy(Duration.ofMinutes(5), Duration.ofMinutes(30)));
        defaults.put(Priority.P2, new SlaPolicy(Duration.ofMinutes(15), Duration.ofHours(2)));
        defaults.put(Priority.P3, new SlaPolicy(Duration.ofHours(1), Duration.ofHours(8)));
        defaults.put(Priority.P4, new SlaPolicy(Duration.ofHours(4), Duration.ofHours(24)));

        defaults.forEach((priority, policy) -> {
            if (!repository.existsById(priority)) {
                repository.save(SlaPolicyEntity.fromDomain(priority, policy));
            }
        });
    }
}
