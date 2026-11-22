package com.iitsm.iitsm.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.iitsm.iitsm.model.DuplicateMatch;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.strategy.similarity.SimilarityStrategy;

@Service
public class DuplicateDetectionService {
    private static final double THRESHOLD = 0.4;

    private final SimilarityStrategy similarity;

    public DuplicateDetectionService(SimilarityStrategy similarity) {
        this.similarity = similarity;
    }

    /** Open incidents that look like the same problem, best match first. */
    public List<DuplicateMatch> findPossibleDuplicates(Incident candidate, List<Incident> existing) {
        List<DuplicateMatch> matches = new ArrayList<>();
        for (Incident other : existing) {
            if (other.getId().equals(candidate.getId())) continue;
            if (isFinished(other)) continue;

            double score = similarity.score(textOf(candidate), textOf(other));
            if (score >= THRESHOLD) {
                matches.add(new DuplicateMatch(other, score));
            }
        }
        matches.sort((x, y) -> Double.compare(y.score(), x.score()));
        return matches;
    }

    private boolean isFinished(Incident incident) {
        IncidentStatus s = incident.getStatus();
        return s == IncidentStatus.RESOLVED
                || s == IncidentStatus.VERIFIED
                || s == IncidentStatus.CLOSED;
    }

    /** Subject and description together (older incidents' subject is derived from the description). */
    private static String textOf(Incident incident) {
        return incident.getSubject() + " " + (incident.getDescription() == null ? "" : incident.getDescription());
    }
}
