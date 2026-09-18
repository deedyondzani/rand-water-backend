package randwater.quality.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import randwater.quality.entity.BackupSnapshot;
import randwater.quality.entity.SystemHealth;
import randwater.quality.repository.*;
import randwater.quality.service.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/joe")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class JoeController {

    private final HealthMonitorService healthMonitor;
    private final BackupService backupService;
    private final AutoHealService autoHeal;
    private final PumpStateRepository pumpRepo;
    private final CylinderStateRepository cylRepo;
    private final TankStateRepository tankRepo;
    private final AuditLogService auditLogService;
    private final PredictionService predictionService;

    // ---------- HEALTH ----------
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        SystemHealth latest = healthMonitor.latest();
        return ResponseEntity.ok(Map.of(
            "current", latest != null ? latest : Map.of("status", "NO_DATA"),
            "history", healthMonitor.recent()
        ));
    }

    // ---------- ANOMALIES ----------
    @GetMapping("/anomalies")
    public ResponseEntity<?> anomalies() {
        List<Map<String, Object>> issues = new ArrayList<>();

        // Pumps running > 24h
        long now = System.currentTimeMillis();
        for (var p : pumpRepo.findAll()) {
            if ("Running".equals(p.getStatus()) && p.getStartTimestamp() != null) {
                long hours = (now - p.getStartTimestamp().toInstant(java.time.ZoneOffset.UTC).toEpochMilli())
                        / (1000 * 60 * 60);
                if (hours >= 1440) {   // 60 days — rotation due
                    issues.add(Map.of(
                        "severity", "HIGH",
                        "type", "LONG_RUN",
                        "message", String.format("%s pump %d has been running continuously for %d days",
                            p.getPlantName(), p.getPumpNumber(), hours / 24)
                    ));
                } else if (hours >= 720) {   // 30 days — rotation recommended
                    issues.add(Map.of(
                        "severity", "WARN",
                        "type", "LONG_RUN",
                        "message", String.format("%s pump %d has been running continuously for %d days",
                            p.getPlantName(), p.getPumpNumber(), hours / 24)
                    ));
                }
            }
        }

        // Tanks low
        for (var t : tankRepo.findAll()) {
            int max = "Palmiet".equalsIgnoreCase(t.getPlantName()) ? 34500 : 14000;
            if (t.getLevel() != null && t.getLevel() * 100 / max < 20) {
                issues.add(Map.of(
                    "severity", "HIGH",
                    "type", "TANK_LOW",
                    "message", String.format("%s tank %d at %d L (<20%%)",
                        t.getPlantName(), t.getTankNumber(), t.getLevel())
                ));
            }
        }

        // Cylinders: no FULL cylinders at a plant
        Map<String, Long> fullPerPlant = new HashMap<>();
        for (var c : cylRepo.findAll()) {
            if ("FULL".equals(c.getStatus())) {
                fullPerPlant.merge(c.getPlantName(), 1L, Long::sum);
            }
        }
        for (String plant : List.of("Palmiet", "Eikenhof", "Zwartkopjes")) {
            if (fullPerPlant.getOrDefault(plant, 0L) == 0) {
                issues.add(Map.of(
                    "severity", "HIGH",
                    "type", "NO_FULL_CYLINDERS",
                    "message", plant + " has 0 FULL chlorine cylinders in stock"
                ));
            }
        }

        // Add C2 + C3 prediction anomalies
        issues.addAll(predictionService.predictionAnomalies());

        return ResponseEntity.ok(Map.of(
            "count", issues.size(),
            "items", issues
        ));
    }

    // ---------- HEAL ----------
    @PostMapping("/heal")
    public ResponseEntity<?> heal() {
        return ResponseEntity.ok(autoHeal.runHeal());
    }

    // ---------- BACKUP ----------
    @PostMapping("/backup")
    public ResponseEntity<?> backup() {
        BackupSnapshot snap = backupService.createBackup("MANUAL", "triggered via Joe console");
        try {
            auditLogService.log("JOE", null, "JOE_BACKUP",
                "Manual backup #" + snap.getId() + " (" + snap.getSizeBytes() + " bytes)", "joe");
        } catch (Exception ignored) {}
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", snap.getId());
        result.put("sizeBytes", snap.getSizeBytes());
        result.put("status", snap.getStatus());
        result.put("notes", snap.getNotes() != null ? snap.getNotes() : "");
        result.put("createdAt", snap.getCreatedAt() != null ? snap.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/backups")
    public List<BackupSnapshot> listBackups() {
        // Don't return huge JSON blobs
        List<BackupSnapshot> list = backupService.recent();
        list.forEach(b -> b.setDataJson(null));
        return list;
    }

    @PostMapping("/restore/{id}")
    public ResponseEntity<?> restore(@PathVariable Long id) {
        BackupSnapshot snap = backupService.get(id);
        if (snap == null || snap.getDataJson() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "snapshot not found or empty"));
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, List<Map<String, Object>>> data =
                mapper.readValue(snap.getDataJson(), Map.class);

            int restored = 0;
            restored += restorePumps(data.get("pump_states"));
            restored += restoreCylinders(data.get("cylinder_states"));
            restored += restoreTanks(data.get("tank_states"));

            auditLogService.log("JOE", null, "JOE_RESTORE",
                "Restored backup #" + id + " (" + restored + " rows)", "joe");

            return ResponseEntity.ok(Map.of("restoredRows", restored, "backupId", id));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private int restorePumps(List<Map<String, Object>> rows) {
        if (rows == null) return 0;
        int count = 0;
        for (Map<String, Object> row : rows) {
            try {
                Long id = ((Number) row.get("id")).longValue();
                String status = (String) row.get("status");
                Number acc = (Number) row.get("accumulatedSeconds");
                pumpRepo.findById(id).ifPresent(p -> {
                    p.setStatus(status);
                    if (acc != null) p.setAccumulatedSeconds(acc.longValue());
                    pumpRepo.save(p);
                });
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int restoreCylinders(List<Map<String, Object>> rows) {
        if (rows == null) return 0;
        int count = 0;
        for (Map<String, Object> row : rows) {
            try {
                Integer id = ((Number) row.get("id")).intValue();
                String status = (String) row.get("status");
                cylRepo.findById(id).ifPresent(c -> {
                    c.setStatus(status);
                    cylRepo.save(c);
                });
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int restoreTanks(List<Map<String, Object>> rows) {
        if (rows == null) return 0;
        int count = 0;
        for (Map<String, Object> row : rows) {
            try {
                Integer id = ((Number) row.get("id")).intValue();
                String status = (String) row.get("status");
                Number level = (Number) row.get("level");
                tankRepo.findById(id).ifPresent(t -> {
                    t.setStatus(status);
                    if (level != null) t.setLevel(level.intValue());
                    tankRepo.save(t);
                });
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    // ---------- C2 — TANK DEPLETION ETAs ----------
    @GetMapping("/tank-etas")
    public ResponseEntity<?> tankEtas() {
        List<Map<String, Object>> etas = predictionService.tankEtas();
        return ResponseEntity.ok(Map.of(
            "count", etas.size(),
            "items", etas
        ));
    }

    // ---------- C3 — PUMP HEALTH ----------
    @GetMapping("/pump-health")
    public ResponseEntity<?> pumpHealth(@RequestParam(required = false) String plant) {
        List<Map<String, Object>> all = predictionService.pumpHealth();
        if (plant != null && !plant.isBlank()) {
            all = all.stream()
                .filter(p -> plant.equalsIgnoreCase((String) p.get("plantName")))
                .collect(java.util.stream.Collectors.toList());
        }
        long healthy = all.stream().filter(p -> "HEALTHY".equals(p.get("grade"))).count();
        long watch = all.stream().filter(p -> "WATCH".equals(p.get("grade"))).count();
        long service = all.stream().filter(p -> "SERVICE".equals(p.get("grade"))).count();
        return ResponseEntity.ok(Map.of(
            "count", all.size(),
            "healthy", healthy,
            "watch", watch,
            "service", service,
            "items", all
        ));
    }

    // ---------- ASK JOE (simple intent router) ----------
    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody Map<String, String> body) {
        String q = (body.getOrDefault("question", "")).toLowerCase();
        String answer;

        if (q.contains("pump") && (q.contains("running") || q.contains("active"))) {
            long running = pumpRepo.findAll().stream().filter(p -> "Running".equals(p.getStatus())).count();
            long total = pumpRepo.count();
            answer = running + " of " + total + " pumps are running across all plants.";
        } else if (q.contains("alarm") || q.contains("alert")) {
            int count = countAnomalies();
            answer = count == 0 ? "No active alarms." : count + " issue(s) detected.";
        } else if (q.contains("backup")) {
            answer = backupService.recent().size() + " backups available.";
        } else if (q.contains("status") || q.contains("health")) {
            SystemHealth h = healthMonitor.latest();
            answer = h == null ? "No health data yet." : "System is " + h.getStatus() + ".";
        } else if (q.contains("cylinder") || q.contains("chlorine")) {
            long full = cylRepo.findAll().stream().filter(c -> "FULL".equals(c.getStatus())).count();
            answer = full + " FULL chlorine cylinders in stock.";
        } else if (q.contains("tank") || q.contains("ammonia")) {
            long inUse = tankRepo.findAll().stream().filter(t -> "In Use".equals(t.getStatus())).count();
            long lowEta = predictionService.tankEtas().stream()
                .filter(t -> !"OK".equals(t.get("severity"))).count();
            answer = inUse + " ammonia tank(s) In Use. " +
                (lowEta > 0 ? lowEta + " with low ETA." : "No immediate depletion.");
        } else if (q.contains("health") && q.contains("pump")) {
            var health = predictionService.pumpHealth();
            long healthy = health.stream().filter(p -> "HEALTHY".equals(p.get("grade"))).count();
            long watch = health.stream().filter(p -> "WATCH".equals(p.get("grade"))).count();
            long service = health.stream().filter(p -> "SERVICE".equals(p.get("grade"))).count();
            answer = String.format("Pump health: %d healthy, %d watch, %d service due.",
                healthy, watch, service);
        } else {
            answer = "Try: 'pumps running', 'alarms', 'backups', 'status', 'cylinders', 'tanks'.";
        }

        return ResponseEntity.ok(Map.of("answer", answer));
    }

    // ---------- Helper: count anomalies (used by /ask) ----------
    private int countAnomalies() {
        int count = 0;
        long now = System.currentTimeMillis();

        for (var p : pumpRepo.findAll()) {
            if ("Running".equals(p.getStatus()) && p.getStartTimestamp() != null) {
                long hours = (now - p.getStartTimestamp()
                    .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()) / (1000 * 60 * 60);
                if (hours >= 720) count++;   // 30 days
            }
        }
        for (var t : tankRepo.findAll()) {
            int max = "Palmiet".equalsIgnoreCase(t.getPlantName()) ? 34500 : 14000;
            if (t.getLevel() != null && t.getLevel() * 100 / max < 20) count++;
        }
        Map<String, Long> fullPerPlant = new HashMap<>();
        for (var c : cylRepo.findAll()) {
            if ("FULL".equals(c.getStatus())) {
                fullPerPlant.merge(c.getPlantName(), 1L, Long::sum);
            }
        }
        for (String plant : List.of("Palmiet", "Eikenhof", "Zwartkopjes")) {
            if (fullPerPlant.getOrDefault(plant, 0L) == 0) count++;
        }
        return count;
    }

}
