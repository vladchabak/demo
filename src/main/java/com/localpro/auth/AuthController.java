package com.localpro.auth;

import com.localpro.user.User;
import com.localpro.user.UserMapper;
import com.localpro.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;

    // The Firebase filter already runs findOrCreateByFirebaseToken before this is called.
    // This endpoint is hit by Flutter on first login to confirm the backend profile is synced.
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@CurrentUser User user) {
        return ResponseEntity.ok(userMapper.toResponse(user));
    }
}
