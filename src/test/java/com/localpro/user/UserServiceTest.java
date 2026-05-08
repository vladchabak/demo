package com.localpro.user;

import com.localpro.user.dto.UpdateProfileRequest;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserMapper userMapper;
    @InjectMocks UserService userService;

    @Test
    void findOrCreate_existingUser_returnsWithoutSaving() {
        User existing = user("uid1", "test@test.com", "Test User");
        when(userRepository.findByFirebaseUid("uid1")).thenReturn(Optional.of(existing));

        User result = userService.findOrCreateByFirebaseToken("uid1", "test@test.com", "Test User");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    void findOrCreate_newUser_savesAndReturns() {
        when(userRepository.findByFirebaseUid("uid2")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.findOrCreateByFirebaseToken("uid2", "new@test.com", "New User");

        assertThat(result.getFirebaseUid()).isEqualTo("uid2");
        assertThat(result.getEmail()).isEqualTo("new@test.com");
        verify(userRepository).save(any());
    }

    @Test
    void getById_existingUser_returnsUser() {
        UUID id = UUID.randomUUID();
        User u = user("uid3", "u@test.com", "User");
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        assertThat(userService.getById(id)).isSameAs(u);
    }

    @Test
    void getById_unknownId_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(id))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void updateProfile_callsMapperAndSaves() {
        UUID id = UUID.randomUUID();
        User u = user("uid4", "u@test.com", "Old Name");
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);

        UpdateProfileRequest req = new UpdateProfileRequest("New Name", null, null, null, null, null);
        userService.updateProfile(id, req);

        verify(userMapper).updateEntity(req, u);
        verify(userRepository).save(u);
    }

    @Test
    void getOrCreateDevUser_withExistingUsers_returnsFirstWithoutCreating() {
        User existing = user("uid5", "dev@test.com", "Existing");
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existing)));

        User result = userService.getOrCreateDevUser();

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    void getOrCreateDevUser_emptyDb_createsAndSavesDevUser() {
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.getOrCreateDevUser();

        assertThat(result.getFirebaseUid()).isEqualTo("dev-user-001");
        assertThat(result.getEmail()).isEqualTo("dev@localpro.com");
        verify(userRepository).save(any());
    }

    @Test
    void updateFcmToken_setsTokenAndSaves() {
        UUID id = UUID.randomUUID();
        User u = user("uid6", "u@test.com", "User");
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);

        userService.updateFcmToken(id, "new-fcm-token");

        assertThat(u.getFcmToken()).isEqualTo("new-fcm-token");
        verify(userRepository).save(u);
    }

    private User user(String uid, String email, String name) {
        return User.builder().firebaseUid(uid).email(email).name(name).build();
    }
}
