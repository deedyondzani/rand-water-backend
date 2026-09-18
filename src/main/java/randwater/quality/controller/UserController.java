package randwater.quality.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import randwater.quality.dto.ActiveUserDTO;
import randwater.quality.dto.PlantRightDTO;
import randwater.quality.dto.UserCreateUpdateRequest;
import randwater.quality.dto.UserDTO;
import randwater.quality.entity.OperatorPlantRight;
import randwater.quality.entity.Role;
import randwater.quality.entity.User;
import randwater.quality.repository.OperatorPlantRightRepository;
import randwater.quality.repository.RoleRepository;
import randwater.quality.repository.UserRepository;
import randwater.quality.service.AuditLogService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired private UserRepository userRepo;
    @Autowired private RoleRepository roleRepo;
    @Autowired private OperatorPlantRightRepository plantRightRepo;
    @Autowired private AuditLogService auditLogService;

    // ---------- helper: map User to DTO ----------
    private UserDTO toDTO(User u) {
        UserDTO dto = new UserDTO();
        dto.setUserId(u.getUserId());
        dto.setUsername(u.getUsername());
        dto.setFullName(u.getFullName());
        dto.setRoleId(u.getRole() != null ? u.getRole().getRoleId() : null);
        dto.setRoleName(u.getRole() != null ? u.getRole().getRoleName() : null);
        dto.setIsActive(u.getIsActive());
        dto.setPasswordResetRequired(u.getPasswordResetRequired());
        dto.setIsOnline(u.getIsOnline());
        dto.setLastLogin(u.getLastLogin());
        dto.setLastActive(u.getLastActive());

        if (u.getRole() != null && "operator".equalsIgnoreCase(u.getRole().getRoleName())) {
            List<OperatorPlantRight> rights = plantRightRepo.findByUserId(u.getUserId());
            List<PlantRightDTO> prDtos = rights.stream()
                .map(r -> new PlantRightDTO(r.getPlantName(), r.getStatus()))
                .collect(Collectors.toList());
            dto.setPlantRights(prDtos);
        } else {
            dto.setPlantRights(new ArrayList<>());
        }
        return dto;
    }

    // ---------- LIST ----------
    @GetMapping("/users")
    public List<UserDTO> listUsers() {
        return userRepo.findAllByOrderByUserIdAsc().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    // ---------- GET ONE ----------
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable Integer id) {
        Optional<User> opt = userRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toDTO(opt.get()));
    }

    // ---------- ROLES ----------
    @GetMapping("/roles")
    public List<Role> listRoles() {
        return roleRepo.findAll();
    }

    // ---------- CREATE ----------
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody UserCreateUpdateRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "username required"));
        if (req.getFullName() == null || req.getFullName().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "fullName required"));
        if (req.getPassword() == null || req.getPassword().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "password required for new users"));
        if (req.getRoleName() == null || req.getRoleName().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "roleName required"));

        if (userRepo.findByUsername(req.getUsername()).isPresent())
            return ResponseEntity.badRequest().body(Map.of("error", "username already exists"));

        Optional<Role> roleOpt = roleRepo.findByRoleName(req.getRoleName());
        if (roleOpt.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "invalid roleName"));

        User u = new User();
        u.setUsername(req.getUsername().trim());
        u.setPasswordHash(req.getPassword());
        u.setFullName(req.getFullName().trim());
        u.setRole(roleOpt.get());
        u.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
        u.setPasswordResetRequired(true);
        u.setIsOnline(false);
        userRepo.save(u);

        if ("operator".equalsIgnoreCase(req.getRoleName()) && req.getPlantRights() != null) {
            for (PlantRightDTO pr : req.getPlantRights()) {
                OperatorPlantRight opr = new OperatorPlantRight();
                opr.setUserId(u.getUserId());
                opr.setPlantName(pr.getPlantName());
                opr.setStatus(pr.getStatus() != null ? pr.getStatus() : "pending");
                plantRightRepo.save(opr);
            }
        }

        try {
            auditLogService.log("admin", null, "USER_CREATE",
                "Created user '" + u.getUsername() + "' with role " + req.getRoleName(), "api");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(toDTO(u));
    }

    // ---------- UPDATE ----------
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Integer id,
                                        @RequestBody UserCreateUpdateRequest req) {
        Optional<User> opt = userRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        User u = opt.get();

        if (req.getFullName() != null && !req.getFullName().isBlank())
            u.setFullName(req.getFullName().trim());

        if (req.getRoleName() != null && !req.getRoleName().isBlank()) {
            Optional<Role> roleOpt = roleRepo.findByRoleName(req.getRoleName());
            if (roleOpt.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "invalid roleName"));
            u.setRole(roleOpt.get());
        }

        if (req.getIsActive() != null) u.setIsActive(req.getIsActive());

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            u.setPasswordHash(req.getPassword());
            u.setPasswordResetRequired(true);
        }

        userRepo.save(u);

        String effectiveRole = u.getRole().getRoleName();
        if ("operator".equalsIgnoreCase(effectiveRole)) {
            if (req.getPlantRights() != null) {
                plantRightRepo.deleteByUserId(u.getUserId());
                for (PlantRightDTO pr : req.getPlantRights()) {
                    OperatorPlantRight opr = new OperatorPlantRight();
                    opr.setUserId(u.getUserId());
                    opr.setPlantName(pr.getPlantName());
                    opr.setStatus(pr.getStatus() != null ? pr.getStatus() : "pending");
                    plantRightRepo.save(opr);
                }
            }
        } else {
            plantRightRepo.deleteByUserId(u.getUserId());
        }

        try {
            auditLogService.log("admin", null, "USER_UPDATE",
                "Updated user '" + u.getUsername() + "'", "api");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(toDTO(u));
    }

    // ---------- DELETE ----------
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Integer id) {
        Optional<User> opt = userRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        User u = opt.get();
        if ("admin".equalsIgnoreCase(u.getRole().getRoleName()) &&
            userRepo.countByRole_RoleName("admin") <= 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "cannot delete last admin"));
        }

        userRepo.delete(u);

        try {
            auditLogService.log("admin", null, "USER_DELETE",
                "Deleted user '" + u.getUsername() + "'", "api");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("deleted", true, "userId", id));
    }

    // ---------- RESET PASSWORD ----------
    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Integer id) {
        Optional<User> opt = userRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        User u = opt.get();
        u.setPasswordHash("temp123");
        u.setPasswordResetRequired(true);
        userRepo.save(u);

        try {
            auditLogService.log("admin", null, "USER_RESET_PASSWORD",
                "Reset password for '" + u.getUsername() + "' to temp123", "api");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("userId", id, "message", "Password reset to temp123"));
    }

    // ---------- ACTIVE USERS (last 24h) ----------
    @GetMapping("/active-users")
    public List<ActiveUserDTO> activeUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        return userRepo.findAllByOrderByUserIdAsc().stream()
            .filter(u -> u.getLastActive() != null && u.getLastActive().isAfter(cutoff))
            .sorted((a, b) -> {
                boolean aOn = a.getIsOnline() != null && a.getIsOnline();
                boolean bOn = b.getIsOnline() != null && b.getIsOnline();
                if (aOn != bOn) return aOn ? -1 : 1;
                if (a.getLastActive() != null && b.getLastActive() != null) {
                    return b.getLastActive().compareTo(a.getLastActive());
                }
                return 0;
            })
            .limit(20)
            .map(u -> new ActiveUserDTO(
                u.getUserId(),
                u.getUsername(),
                u.getFullName(),
                u.getRole() != null ? u.getRole().getRoleName() : null,
                u.getIsOnline() != null ? u.getIsOnline() : false,
                u.getLastLogin(),
                u.getLastActive()
            ))
            .collect(Collectors.toList());
    }

    // ---------- HEARTBEAT ----------
    @PostMapping("/heartbeat/{userId}")
    public ResponseEntity<?> heartbeat(@PathVariable Integer userId) {
        Optional<User> opt = userRepo.findById(userId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        User u = opt.get();
        u.setLastActive(LocalDateTime.now());
        u.setIsOnline(true);
        if (u.getLastLogin() == null) u.setLastLogin(LocalDateTime.now());
        userRepo.save(u);
        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "isOnline", true,
            "lastActive", u.getLastActive()
        ));
    }

    // ---------- LOGOUT ----------
    @PostMapping("/logout/{userId}")
    public ResponseEntity<?> logout(@PathVariable Integer userId) {
        Optional<User> opt = userRepo.findById(userId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        User u = opt.get();
        u.setIsOnline(false);
        u.setLastActive(LocalDateTime.now());
        userRepo.save(u);
        return ResponseEntity.ok(Map.of("userId", userId, "isOnline", false));
    }
}
