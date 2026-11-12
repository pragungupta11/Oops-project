package com.iitsm.iitsm.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.iitsm.iitsm.model.Incident;

@Component
public class InMemoryIncidentSource implements IncidentSource {
    private final List<Incident> incidents = new ArrayList<>();

    public void add(Incident incident) { incidents.add(incident); }

    @Override
    public List<Incident> findOpenIncidents() { return new ArrayList<>(incidents); }
}