package com.localpro.user;

import com.localpro.user.dto.UpdateProfileRequest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public User findOrCreateByFirebaseToken(String uid, String email, String name) {
        return userRepository.findByFirebaseUid(uid).orElseGet(() -> {
            User user = User.builder()
                    .firebaseUid(uid)
                    .email(email)
                    .name(name)
                    .build();
            return userRepository.save(user);
        });
    }

    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getById(userId);
        userMapper.updateEntity(request, user);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    public void updateFcmToken(UUID userId, String token) {
        User user = getById(userId);
        user.setFcmToken(token);
        userRepository.save(user);
    }
}
