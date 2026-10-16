package com.example.fams.gl;

import com.example.fams.core.config.AuthenticationManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** Admin-only endpoints for generating journals for transactions that predate GL generation. */
@RestController
@RequestMapping("/gl/backfill")
public class GLBackfillController {

    private final GLBackfillService backfillService;
    private final AuthenticationManager authenticationManager;

    public GLBackfillController(GLBackfillService backfillService, AuthenticationManager authenticationManager) {
        this.backfillService = backfillService;
        this.authenticationManager = authenticationManager;
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, boolean success, Object data, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", success);
        if (data != null) body.put("data", data);
        if (message != null) body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview() {
        if (!authenticationManager.isAdmin()) {
            return response(HttpStatus.FORBIDDEN, false, null, "Only administrators can run a backfill.");
        }
        return response(HttpStatus.OK, true, backfillService.preview(), null);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> run(@RequestParam(defaultValue = "false") boolean autoPost,
                                                   Authentication authentication) {
        if (!authenticationManager.isAdmin()) {
            return response(HttpStatus.FORBIDDEN, false, null, "Only administrators can run a backfill.");
        }
        String who = authentication != null && authentication.getName() != null ? authentication.getName() : "admin";
        try {
            return response(HttpStatus.OK, true, backfillService.run(autoPost, "backfill (" + who + ")"), null);
        } catch (IllegalStateException ex) {
            return response(HttpStatus.CONFLICT, false, null, ex.getMessage());
        } catch (Exception ex) {
            return response(HttpStatus.INTERNAL_SERVER_ERROR, false, null, "Backfill failed: " + ex.getMessage());
        }
    }
}
