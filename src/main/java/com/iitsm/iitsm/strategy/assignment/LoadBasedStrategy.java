package com.iitsm.iitsm.strategy.assignment;

import java.util.Comparator;
import java.util.List;

import com.iitsm.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.iitsm.model.Technician;

import org.springframework.stereotype.Component;

@Component("loadBased")

public class LoadBasedStrategy implements AssignmentStrategy {
    @Override
    public Technician pick(String category, List<Technician> technicians) {
        return technicians.stream()
            .filter(Technician::isAvailable)
            .min(Comparator.comparingInt(Technician::getActiveIncidents))
            .orElseThrow(() -> new TechnicianUnavailableException("No technician available"));
    }
}
