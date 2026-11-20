package com.iitsm.iitsm.notification;

/**
 * One way of delivering a notification (in-app, e-mail, ...). To add SMS or push later,
 * write one more class implementing this interface and mark it @Component: the
 * NotificationService picks it up automatically, nothing else changes.
 */
public interface NotificationChannel {
    String name();

    void send(Notification notification);
}
