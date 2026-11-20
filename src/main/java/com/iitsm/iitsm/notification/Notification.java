package com.iitsm.iitsm.notification;

/** A message for one user, independent of how it is delivered. */
public record Notification(String recipient, String subject, String body, String incidentId) {}
