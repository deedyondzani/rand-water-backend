package randwater.quality.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import randwater.quality.entity.OperatorPlantRight;
import randwater.quality.entity.User;
import randwater.quality.repository.OperatorPlantRightRepository;
import randwater.quality.repository.UserRepository;
import randwater.quality.service.AuditLogService;
import randwater.quality.service.JwtService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired private UserRepository userRepo;
    @Autowired private OperatorPlantRightRepository plantRightRepo;
    @Autowired private JwtService jwtService;
    @Autowired private AuditLogService auditLogService;

    public static class LoginRequest {
        public String username;
        public String password;
    }

    public static class ChangePasswordRequest {
        public String currentPassword;
        public String newPassword;
    }

    // ---------- helper: fetch plant rights as list of maps ----------
    private List<Map<String, String>> getPlantRights(Integer userId, String role) {
        List<Map<String, String>> result = new ArrayList<>();
        if (userId == null) return result;
        if (!"operator".equalsIgnoreCase(role)) return result;
        List<OperatorPlantRight> rights = plantRightRepo.findByUserId(userId);
        for (OperatorPlantRight r : rights) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("plantName", r.getPlantName());
            m.put("status", r.getStatus());
            result.add(m);
        }
        return result;
    }

    // ---------- LOGIN ----------
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (req == null || req.username == null || req.password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and password required"));
        }

        Optional<User> userOpt = userRepo.findByUsername(req.username.trim());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid credentials"));
        }

        User user = userOpt.get();

        if (user.getIsActive() == null || !user.getIsActive()) {
            return ResponseEntity.status(403).body(Map.of("error", "account is disabled"));
        }

        if (!req.password.equals(user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid credentials"));
        }

        String token = jwtService.generateToken(user);
        String role = user.getRole() != null ? user.getRole().getRoleName() : "operator";

        user.setLastLogin(LocalDateTime.now());
        user.setLastActive(LocalDateTime.now());
        user.setIsOnline(true);
        userRepo.save(user);

        try {
            auditLogService.log(user.getUsername(), null, "LOGIN",
                user.getUsername() + " logged in", "api");
        } catch (Exception ignored) {}

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("userId", user.getUserId());
        body.put("username", user.getUsername());
        body.put("fullName", user.getFullName());
        body.put("role", role);
        body.put("roleId", user.getRole() != null ? user.getRole().getRoleId() : 3);
        body.put("passwordResetRequired", user.getPasswordResetRequired() != null ? user.getPasswordResetRequired() : false);
        body.put("plantRights", getPlantRights(user.getUserId(), role));

        return ResponseEntity.ok(body);
    }

    // ---------- ME ----------
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthenticated"));
        }
        Optional<User> userOpt = userRepo.findByUsername(auth.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "user not found"));
        }
        User u = userOpt.get();
        String role = u.getRole() != null ? u.getRole().getRoleName() : "operator";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", u.getUserId());
        body.put("username", u.getUsername());
        body.put("fullName", u.getFullName());
        body.put("role", role);
        body.put("roleId", u.getRole() != null ? u.getRole().getRoleId() : 3);
        body.put("isActive", u.getIsActive());
        body.put("passwordResetRequired", u.getPasswordResetRequired() != null ? u.getPasswordResetRequired() : false);
        body.put("lastLogin", u.getLastLogin() != null ? u.getLastLogin().toString() : "");
        body.put("plantRights", getPlantRights(u.getUserId(), role));
        return ResponseEntity.ok(body);
    }

    // ---------- LOGOUT ----------
    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthenticated"));
        }
        Optional<User> userOpt = userRepo.findByUsername(auth.getName());
        if (userOpt.isPresent()) {
            User u = userOpt.get();
            u.setIsOnline(false);
            u.setLastActive(LocalDateTime.now());
            userRepo.save(u);
            try {
                auditLogService.log(u.getUsername(), null, "LOGOUT",
                    u.getUsername() + " logged out", "api");
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(Map.of("message", "logged out"));
    }

    // ---------- CHANGE PASSWORD ----------
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(Authentication auth,
                                            @RequestBody ChangePasswordRequest req) {
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthenticated"));
        }
        if (req == null || req.newPassword == null || req.newPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "newPassword required"));
        }
        if (req.newPassword.trim().length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "password too short (min 4)"));
        }

        Optional<User> opt = userRepo.findByUsername(auth.getName());
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "user not found"));
        User u = opt.get();

        boolean forcedReset = u.getPasswordResetRequired() != null && u.getPasswordResetRequired();
        if (!forcedReset) {
            if (req.currentPassword == null || !req.currentPassword.equals(u.getPasswordHash())) {
                return ResponseEntity.status(403).body(Map.of("error", "current password incorrect"));
            }
        }

        u.setPasswordHash(req.newPassword.trim());
        u.setPasswordResetRequired(false);
        userRepo.save(u);

        try {
            auditLogService.log(u.getUsername(), null, "PASSWORD_CHANGED",
                "User '" + u.getUsername() + "' changed password", "api");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("success", true, "message", "Password updated"));
    }
}
