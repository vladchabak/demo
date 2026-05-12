package com.localpro.notification;

import java.util.UUID;

record NotificationJob(String fcmToken, UUID chatId, String title, String body, int attempts) {
    NotificationJob withNextAttempt() {
        return new NotificationJob(fcmToken, chatId, title, body, attempts + 1);
    }
}
