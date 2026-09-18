package randwater.quality.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import randwater.quality.dto.PlantRightDTO;
import randwater.quality.entity.OperatorPlantRight;
import randwater.quality.entity.User;
import randwater.quality.repository.OperatorPlantRightRepository;
import randwater.quality.repository.UserRepository;
import randwater.quality.service.AuditLogService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class AccessRequestController {

    @Autowired private OperatorPlantRightRepository plantRightRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private AuditLogService auditLogService;

    private static final List<String> VALID_PLANTS = List.of("Palmiet", "Eikenhof", "Zwartkopjes", "Mapleton");

    // ---------- DTO wrapper ----------
    public static class AccessRequestDTO {
        public Integer id;
        public Integer userId;
        public String username;
        public String fullName;
        public String plantName;
        public String status;
        public LocalDateTime requestedAt;
        public LocalDateTime reviewedAt;
        public Integer reviewedBy;

        public AccessRequestDTO(OperatorPlantRight r, User u) {
            this.id = r.getId();
            this.userId = r.getUserId();
            this.plantName = r.getPlantName();
            this.status = r.getStatus();
            this.requestedAt = r.getRequestedAt();
            this.reviewedAt = r.getReviewedAt();
            this.reviewedBy = r.getReviewedBy();
            if (u != null) {
                this.username = u.getUsername();
                this.fullName = u.getFullName();
            }
        }
    }

    // ---------- LIST PENDING (admin + supervisor) ----------
    @GetMapping("/api/admin/access-requests")
    public List<AccessRequestDTO> listPending() {
        return plantRightRepo.findByStatusOrderByRequestedAtAsc("pending").stream()
            .map(r -> new AccessRequestDTO(r, userRepo.findById(r.getUserId()).orElse(null)))
            .collect(Collectors.toList());
    }

    // ---------- LIST ALL (with filter) ----------
    @GetMapping("/api/admin/access-requests/all")
    public List<AccessRequestDTO> listAll(@RequestParam(required = false) String status) {
        List<OperatorPlantRight> all;
        if (status != null && !status.isBlank()) {
            all = plantRightRepo.findAll().stream()
                .filter(r -> status.equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.toList());
        } else {
            all = plantRightRepo.findAll();
        }
        return all.stream()
            .map(r -> new AccessRequestDTO(r, userRepo.findById(r.getUserId()).orElse(null)))
            .collect(Collectors.toList());
    }

    // ---------- APPROVE ----------
    @PostMapping("/api/admin/access-requests/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Integer id,
                                     @RequestParam(required = false) Integer reviewerId) {
        Optional<OperatorPlantRight> opt = plantRightRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        OperatorPlantRight right = opt.get();
        if (!"pending".equals(right.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "request is not pending"));
        }
        right.setStatus("approved");
        right.setReviewedAt(LocalDateTime.now());
        right.setReviewedBy(reviewerId != null ? reviewerId : 1);
        plantRightRepo.save(right);

        try {
            User u = userRepo.findById(right.getUserId()).orElse(null);
            String uname = u != null ? u.getUsername() : ("user#" + right.getUserId());
            auditLogService.log("admin", right.getPlantName(), "ACCESS_APPROVED",
                "Approved " + uname + " access to " + right.getPlantName(), "api");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("id", id, "status", "approved"));
    }

    // ---------- DENY ----------
    @PostMapping("/api/admin/access-requests/{id}/deny")
    public ResponseEntity<?> deny(@PathVariable Integer id,
                                  @RequestParam(required = false) Integer reviewerId) {
        Optional<OperatorPlantRight> opt = plantRightRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        OperatorPlantRight right = opt.get();
        if (!"pending".equals(right.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "request is not pending"));
        }
        right.setStatus("denied");
        right.setReviewedAt(LocalDateTime.now());
        right.setReviewedBy(reviewerId != null ? reviewerId : 1);
        plantRightRepo.save(right);

        try {
            User u = userRepo.findById(right.getUserId()).orElse(null);
            String uname = u != null ? u.getUsername() : ("user#" + right.getUserId());
            auditLogService.log("admin", right.getPlantName(), "ACCESS_DENIED",
                "Denied " + uname + " access to " + right.getPlantName(), "api");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("id", id, "status", "denied"));
    }

    // ---------- OPERATOR REQUEST (self-service) ----------
    public static class PlantRequestPayload {
        public Integer userId;
        public String plantName;
    }

    @PostMapping("/api/operator/plant-request")
    public ResponseEntity<?> requestAccess(@RequestBody PlantRequestPayload payload) {
        if (payload.userId == null || payload.plantName == null || payload.plantName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId and plantName required"));
        }
        if (!VALID_PLANTS.contains(payload.plantName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid plantName"));
        }

        Optional<User> userOpt = userRepo.findById(payload.userId);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

        // Check if right already exists
        Optional<OperatorPlantRight> existing = plantRightRepo.findByUserId(payload.userId).stream()
            .filter(r -> payload.plantName.equalsIgnoreCase(r.getPlantName()))
            .findFirst();

        OperatorPlantRight right;
        if (existing.isPresent()) {
            right = existing.get();
            if ("approved".equals(right.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "already approved"));
            }
            right.setStatus("pending");
            right.setRequestedAt(LocalDateTime.now());
            right.setReviewedAt(null);
            right.setReviewedBy(null);
        } else {
            right = new OperatorPlantRight();
            right.setUserId(payload.userId);
            right.setPlantName(payload.plantName);
            right.setStatus("pending");
        }
        plantRightRepo.save(right);

        try {
            auditLogService.log(userOpt.get().getUsername(), payload.plantName, "ACCESS_REQUESTED",
                userOpt.get().getUsername() + " requested access to " + payload.plantName, "api");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(new AccessRequestDTO(right, userOpt.get()));
    }

    // ---------- OPERATOR: own rights ----------
    @GetMapping("/api/operator/my-rights/{userId}")
    public List<PlantRightDTO> myRights(@PathVariable Integer userId) {
        return plantRightRepo.findByUserId(userId).stream()
            .map(r -> new PlantRightDTO(r.getPlantName(), r.getStatus()))
            .collect(Collectors.toList());
    }
}
