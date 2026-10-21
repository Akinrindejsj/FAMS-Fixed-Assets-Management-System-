package com.example.fams.governance;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Records state-changing requests (POST, PUT, PATCH, DELETE) by signed-in users for the
 * User Activity Report. Only the method, path, outcome and time are stored — never query
 * strings or request bodies. Logging failures never affect the request.
 */
@Component
public class UserActivityInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UserActivityInterceptor.class);
    private static final Set<String> TRACKED_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final UserActivityLogRepository repository;

    public UserActivityInterceptor(UserActivityLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            if (!TRACKED_METHODS.contains(request.getMethod())) {
                return;
            }
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
                return;
            }
            String path = request.getRequestURI();
            if (path == null) {
                return;
            }
            UserActivityLog entry = new UserActivityLog();
            entry.setUsername(authentication.getName().length() > 160 ? authentication.getName().substring(0, 160) : authentication.getName());
            entry.setHttpMethod(request.getMethod());
            entry.setPath(path.length() > 255 ? path.substring(0, 255) : path);
            entry.setStatusCode(response.getStatus());
            repository.save(entry);
        } catch (Exception e) {
            log.debug("Could not record user activity: {}", e.getMessage());
        }
    }
}
