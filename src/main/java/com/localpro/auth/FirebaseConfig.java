package com.localpro.auth;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${FIREBASE_SERVICE_ACCOUNT_JSON:}")
    private String serviceAccountJson;

    @Bean
    public FirebaseApp firebaseApp() {
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            log.warn("Firebase not configured - running in dev mode, all requests will be unauthenticated");
            return null;
        }
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                return FirebaseApp.getInstance();
            }
            InputStream serviceAccount = new ByteArrayInputStream(
                    serviceAccountJson.getBytes(StandardCharsets.UTF_8));
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("Firebase initialized successfully");
            return app;
        } catch (Exception e) {
            log.error("Failed to initialize Firebase: {} - falling back to dev mode", e.getMessage());
            return null;
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        if (firebaseApp == null) {
            return null;
        }
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
