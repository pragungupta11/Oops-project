package com.iitsm.iitsm.service;

import java.util.List;

import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.model.Priority;
import com.iitsm.iitsm.model.ProcessingResult;
import com.iitsm.iitsm.model.Technician;
import com.iitsm.iitsm.strategy.assignment.AssignmentStrategy;
import com.iitsm.iitsm.strategy.priority.PriorityStrategy;

import org.springframework.stereotype.Service;

@Service

public class IncidentProcessingService {
    private final PriorityStrategy priorityStrategy;
    private volatile AssignmentStrategy assignmentStrategy;
    private final SlaService slaService;

    public IncidentProcessingService(PriorityStrategy priorityStrategy,
                                     AssignmentStrategy assignmentStrategy,
                                     SlaService slaService) {
        this.priorityStrategy = priorityStrategy;
        this.assignmentStrategy = assignmentStrategy;
        this.slaService = slaService;
    }

    /** Lets an admin switch the assignment algorithm at runtime. */
    public void setAssignmentStrategy(AssignmentStrategy assignmentStrategy) {
        this.assignmentStrategy = assignmentStrategy;
    }

    public AssignmentStrategy getAssignmentStrategy() {
        return assignmentStrategy;
    }

    public ProcessingResult process(Incident incident, List<Technician> technicians) {
        Priority priority = priorityStrategy.calculate(
                incident.getImpact(), incident.getUrgency(), incident.getCriticality());
        incident.setPriority(priority);
        incident.transitionTo(IncidentStatus.CLASSIFIED);

        Technician technician = assignmentStrategy.pick(incident.getCategory(), technicians);
        technician.incrementActiveIncidents();
        incident.setAssignedTechnician(technician);
        incident.transitionTo(IncidentStatus.ASSIGNED);

        return new ProcessingResult(
                priority,
                technician,
                slaService.responseDeadline(incident.getCreatedAt(), priority),
                slaService.resolutionDeadline(incident.getCreatedAt(), priority));
    }
}