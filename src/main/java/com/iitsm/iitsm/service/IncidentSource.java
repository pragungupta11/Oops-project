package com.iitsm.iitsm.service;

import java.util.List;

import com.iitsm.iitsm.model.Incident;

public interface IncidentSource {
    List<Incident> findOpenIncidents();
}