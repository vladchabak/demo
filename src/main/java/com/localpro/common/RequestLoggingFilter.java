package com.localpro.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        log.info(">>> {} {} | User-Agent: {}",
            request.getMethod(),
            request.getRequestURI(),
            request.getHeader("User-Agent") != null ? "present" : "none");

        chain.doFilter(request, response);

        long duration = System.currentTimeMillis() - startTime;
        int status = response.getStatus();

        if (status >= 400) {
            log.warn("<<< {} {} => {} ({}ms)",
                request.getMethod(), request.getRequestURI(), status, duration);
        } else {
            log.debug("<<< {} {} => {} ({}ms)",
                request.getMethod(), request.getRequestURI(), status, duration);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/actuator/health") ||
               path.contains("/actuator/info");
    }
}
