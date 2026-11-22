package com.iitsm.iitsm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.model.DuplicateMatch;
import com.iitsm.iitsm.model.Incident;
import com.iitsm.iitsm.model.IncidentStatus;
import com.iitsm.iitsm.strategy.similarity.JaccardSimilarityStrategy;

class DuplicateDetectionServiceTest {
    private final LocalDateTime created = LocalDateTime.of(2026, 11, 12, 10, 0);
    private final DuplicateDetectionService service =
            new DuplicateDetectionService(new JaccardSimilarityStrategy());

    private Incident incident(String id, String description) {
        return new Incident(id, description, "Application", 2, 2, 2, created);
    }

    @Test
    void findsTheRelatedPaymentIncidentsFromTheProblemStatement() {
        Incident inc101 = incident("INC101", "Payment application unavailable");
        Incident inc102 = incident("INC102", "Payment transaction failing");
        Incident inc103 = incident("INC103", "Unable to make payment");
        Incident unrelated = incident("INC104", "Wifi is slow in the library");

        List<DuplicateMatch> matches =
                service.findPossibleDuplicates(inc103, List.of(inc101, inc102, unrelated));

        assertEquals(2, matches.size());
    }

    @Test
    void unrelatedIncidentsAreNotFlagged() {
        Incident a = incident("A", "Payment application unavailable");
        Incident b = incident("B", "Wifi is slow in the library");

        assertTrue(service.findPossibleDuplicates(a, List.of(b)).isEmpty());
    }

    @Test
    void anIncidentIsNeverItsOwnDuplicate() {
        Incident a = incident("A", "Payment application unavailable");

        assertTrue(service.findPossibleDuplicates(a, List.of(a)).isEmpty());
    }

    @Test
    void closedIncidentsAreIgnored() {
        Incident candidate = incident("A", "Payment application unavailable");
        Incident closed = incident("B", "Payment application unavailable");
        closed.setStatus(IncidentStatus.CLOSED);

        assertTrue(service.findPossibleDuplicates(candidate, List.of(closed)).isEmpty());
    }

    @Test
    void bestMatchComesFirst() {
        Incident candidate = incident("A", "Payment application unavailable");
        Incident weaker = incident("B", "Payment transaction failing");
        Incident stronger = incident("C", "Payment unavailable");

        List<DuplicateMatch> matches =
                service.findPossibleDuplicates(candidate, List.of(weaker, stronger));

        assertEquals("C", matches.get(0).incident().getId());
        assertEquals("B", matches.get(1).incident().getId());
    }

    @Test
    void matchingSubjectsAreFoundEvenWhenDescriptionsDiffer() {
        Incident original = incident("A", "Customers cannot complete checkout since 10 am");
        original.setSubject("Payment app is down");
        Incident second = incident("B", "Customers see an error when they pay");
        second.setSubject("Payment application unavailable");
        Incident third = incident("C", "The pay button does nothing");
        third.setSubject("Unable to make payment");

        assertEquals(1, service.findPossibleDuplicates(second, List.of(original)).size());
        assertEquals(2, service.findPossibleDuplicates(third, List.of(original, second)).size());
    }

    @Test
    void differentSubjectsWithDifferentDescriptionsAreNotFlagged() {
        Incident a = incident("A", "Customers cannot complete checkout");
        a.setSubject("Payment app is down");
        Incident b = incident("B", "Nobody on floor 3 can connect");
        b.setSubject("Wifi is slow in the library");

        assertTrue(service.findPossibleDuplicates(a, List.of(b)).isEmpty());
    }
}
