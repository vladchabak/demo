package com.localpro.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class NotificationService {

    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;

    @Async
    public void sendMessageNotification(String fcmToken, UUID chatId, String senderName) {
        if (firebaseMessaging == null) {
            log.debug("Firebase not configured — skipping push notification for chat {}", chatId);
            return;
        }
        try {
            Message message = Message.builder()
                .setToken(fcmToken)
                .putData("chat_id", chatId.toString())
                .setNotification(Notification.builder()
                    .setTitle(senderName)
                    .setBody("Sent you a message")
                    .build())
                .build();
            firebaseMessaging.send(message);
        } catch (FirebaseMessagingException e) {
            log.error("FCM send failed: {}", e.getMessage());
        }
    }
}
