package com.localpro.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
public class NotificationService {

    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;

    private final ConcurrentLinkedQueue<NotificationJob> retryQueue = new ConcurrentLinkedQueue<>();

    @Async
    public void sendMessageNotification(String fcmToken, UUID chatId, String senderName) {
        if (firebaseMessaging == null) {
            log.debug("Firebase not configured — skipping push notification for chat {}", chatId);
            return;
        }
        String body = "Sent you a message";
        try {
            doSend(fcmToken, chatId, senderName, body);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM send failed for chat {}, queuing for retry: {}", chatId, e.getMessage());
            retryQueue.add(new NotificationJob(fcmToken, chatId, senderName, body, 1));
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void retryFailed() {
        if (firebaseMessaging == null || retryQueue.isEmpty()) return;
        int size = retryQueue.size();
        for (int i = 0; i < size; i++) {
            NotificationJob job = retryQueue.poll();
            if (job == null) break;
            try {
                doSend(job.fcmToken(), job.chatId(), job.title(), job.body());
            } catch (FirebaseMessagingException e) {
                if (job.attempts() >= 3) {
                    log.error("FCM retry exhausted after {} attempts for chat {}: {}",
                            job.attempts(), job.chatId(), e.getMessage());
                } else {
                    retryQueue.add(job.withNextAttempt());
                }
            }
        }
    }

    private void doSend(String fcmToken, UUID chatId, String title, String body)
            throws FirebaseMessagingException {
        Message message = Message.builder()
                .setToken(fcmToken)
                .putData("chat_id", chatId.toString())
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();
        firebaseMessaging.send(message);
    }
}
