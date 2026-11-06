package com.iitsm.iitsm.strategy.assignment;

import java.util.Comparator;
import java.util.List;

import com.iitsm.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.iitsm.model.Technician;

public class SkillBasedStrategy implements AssignmentStrategy {
    @Override
    public Technician pick(String category, List<Technician> technicians) {
        return technicians.stream()
            .filter(Technician::isAvailable)
            .filter(t -> t.getSkills().contains(category))
            .min(Comparator.comparingInt(Technician::getActiveIncidents))
            .orElseThrow(() -> new TechnicianUnavailableException("No skilled technician for " + category));
    }
}