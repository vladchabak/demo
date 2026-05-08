package com.localpro.chat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.localpro.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final UserRepository userRepository;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String authHeader = servletRequest.getServletRequest().getHeader("Authorization");
            if (devMode && "Bearer dev-token".equals(authHeader)) {
                userRepository.findAll(PageRequest.of(0, 1)).stream()
                        .findFirst()
                        .ifPresent(user -> attributes.put("userId", user.getId().toString()));
                return true;
            }

            String token = servletRequest.getServletRequest().getParameter("token");
            if (token != null && !FirebaseApp.getApps().isEmpty()) {
                try {
                    FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
                    userRepository.findByFirebaseUid(decoded.getUid())
                            .ifPresent(user -> attributes.put("userId", user.getId().toString()));
                } catch (FirebaseAuthException e) {
                    log.debug("WebSocket token verification failed: {}", e.getMessage());
                }
            }
        }
        return true; // always allow the HTTP upgrade; message-level auth handles rejection
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}
}
