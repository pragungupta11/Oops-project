package com.iitsm.iitsm.strategy.assignment;

import java.util.List;

import com.iitsm.iitsm.model.Technician;

public interface AssignmentStrategy {
    Technician pick(String category, List<Technician> technicians);
}