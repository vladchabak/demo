package com.localpro.user;

import com.localpro.auth.CurrentUser;
import com.localpro.user.dto.UpdateProfileRequest;
import com.localpro.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@CurrentUser User user) {
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@CurrentUser User user,
                                                      @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userMapper.toResponse(userService.updateProfile(user.getId(), request)));
    }

    @PutMapping("/me/fcm-token")
    public ResponseEntity<Void> updateFcmToken(@CurrentUser User user,
                                               @RequestBody Map<String, String> body) {
        userService.updateFcmToken(user.getId(), body.get("token"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userMapper.toResponse(userService.getById(id)));
    }
}
