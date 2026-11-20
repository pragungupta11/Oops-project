package com.iitsm.iitsm.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    /** A channel that just remembers what it was asked to send. */
    static class RecordingChannel implements NotificationChannel {
        final List<Notification> sent = new ArrayList<>();
        @Override public String name() { return "recording"; }
        @Override public void send(Notification n) { sent.add(n); }
    }

    static class BrokenChannel implements NotificationChannel {
        @Override public String name() { return "broken"; }
        @Override public void send(Notification n) { throw new IllegalStateException("mail server down"); }
    }

    @Test
    void everyRecipientGetsTheMessageOnEveryChannel() {
        RecordingChannel a = new RecordingChannel();
        RecordingChannel b = new RecordingChannel();
        NotificationService service = new NotificationService(List.of(a, b), null);

        service.notify(List.of("employee1", "tech2"), "INC1 received", "body", "INC1");

        assertEquals(2, a.sent.size());
        assertEquals(2, b.sent.size());
    }

    @Test
    void aBrokenChannelDoesNotStopTheOthers() {
        RecordingChannel ok = new RecordingChannel();
        NotificationService service = new NotificationService(List.of(new BrokenChannel(), ok), null);

        service.notify(List.of("employee1"), "INC1 received", "body", "INC1");   // must not throw

        assertEquals(1, ok.sent.size());
    }

    @Test
    void duplicateAndEmptyRecipientsAreIgnored() {
        RecordingChannel ok = new RecordingChannel();
        NotificationService service = new NotificationService(List.of(ok), null);

        service.notify(Arrays.asList("admin", "admin", null, " "), "SLA violated", "body", "INC1");

        assertEquals(1, ok.sent.size());
    }
}
