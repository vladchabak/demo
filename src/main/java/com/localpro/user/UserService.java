package com.localpro.user;

import com.localpro.user.dto.UpdateProfileRequest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public User findOrCreateByFirebaseToken(String uid, String email, String name) {
        log.info("=== [UserService.findOrCreateByFirebaseToken] called with uid: {}, email: {}", uid, email);
        return userRepository.findByFirebaseUid(uid).orElseGet(() -> {
            log.info("Creating new user with firebaseUid: {} and email: {}", uid, email);
            User user = User.builder()
                    .firebaseUid(uid)
                    .email(email)
                    .name(name)
                    .build();
            User savedUser = userRepository.save(user);
            log.info("User {} created successfully", savedUser.getId());
            return savedUser;
        });
    }

    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        log.info("=== [UserService.updateProfile] called for userId: {}", userId);
        User user = getById(userId);
        userMapper.updateEntity(request, user);
        User updated = userRepository.save(user);
        log.info("User {} profile updated", userId);
        return updated;
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", id);
                    return new EntityNotFoundException("User not found: " + id);
                });
    }

    public User getOrCreateDevUser() {
        log.info("=== [UserService.getOrCreateDevUser] called");
        List<User> users = userRepository.findAll(PageRequest.of(0, 1)).getContent();
        if (!users.isEmpty()) {
            log.info("Returning existing dev user: {}", users.get(0).getId());
            return users.get(0);
        }
        log.info("Creating new dev user");
        User devUser = User.builder()
                .firebaseUid("dev-user-001")
                .email("dev@localpro.com")
                .name("Dev User")
                .role(UserRole.BOTH)
                .build();
        User saved = userRepository.save(devUser);
        log.info("Dev user {} created", saved.getId());
        return saved;
    }

    public void updateFcmToken(UUID userId, String token) {
        log.info("=== [UserService.updateFcmToken] called for userId: {}", userId);
        User user = getById(userId);
        user.setFcmToken(token);
        userRepository.save(user);
        log.info("FCM token updated for user {}", userId);
    }
}
