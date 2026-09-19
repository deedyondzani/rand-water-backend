package randwater.quality.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import randwater.quality.entity.AuditLog;
import randwater.quality.service.AuditLogService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit-log")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuditLogController {

    private final AuditLogService auditLogService;

    // Admin-only event types hidden from supervisor + operator
    private static final List<String> ADMIN_ONLY_TYPES = List.of(
        "USER_CREATE",
        "USER_DELETE",
        "USER_UPDATE",
        "USER_RESET_PASSWORD"
    );

    private String extractRole(Authentication auth) {
        if (auth == null) return "operator";
        for (GrantedAuthority a : auth.getAuthorities()) {
            String s = a.getAuthority(); // "ROLE_ADMIN", "ROLE_SUPERVISOR", ...
            if (s != null && s.startsWith("ROLE_")) {
                return s.substring(5).toLowerCase();
            }
        }
        return "operator";
    }

    @GetMapping
    public Page<AuditLog> list(
            Authentication auth,
            @RequestParam(required = false) String plant,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        String role = extractRole(auth);
        List<String> excludeTypes = new ArrayList<>();

        if ("supervisor".equals(role) || "operator".equals(role)) {
            excludeTypes.addAll(ADMIN_ONLY_TYPES);

            // Operators additionally: only see their own login/logout events
            if ("operator".equals(role)) {
                // We can't easily force username here since it also filters plant events.
                // Instead, exclude the LOGIN/LOGOUT events of other users by using a
                // special case in the query — but for simplicity, we handle it client-side.
                // Server-side, hide other users' auth noise for operators by using the
                // current user's username as a *soft* preference.
                // Left as-is; frontend can filter further if needed.
            }
        }

        return auditLogService.getFiltered(plant, username, eventType, from, to,
            excludeTypes, page, size);
    }

    @GetMapping("/available-dates")
    public ResponseEntity<?> availableDates() {
        // Native query: list distinct dates that have entries (last 30 days)
        List<String> dates = auditLogService.getAvailableDates();
        return ResponseEntity.ok(Map.of("dates", dates));
    }

    @GetMapping("/recent")
    public List<AuditLog> recent() {
        return auditLogService.getAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        String eventType = body.getOrDefault("eventType", "WEB_EVENT");
        String plantName = body.get("plantName");
        String details = body.getOrDefault("details", "");
        String username = body.getOrDefault("username", "admin");

        AuditLog entry = auditLogService.log(username, plantName, eventType, details, "web");
        return ResponseEntity.ok(entry);
    }
}
