package randwater.quality.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import randwater.quality.dto.PumpDTO;
import randwater.quality.entity.PumpConfig;
import randwater.quality.entity.PumpState;
import randwater.quality.repository.PumpConfigRepository;
import randwater.quality.repository.PumpStateRepository;
import randwater.quality.service.AuditLogService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class PumpController {

    @Autowired private PumpStateRepository pumpStateRepository;
    @Autowired private PumpConfigRepository pumpConfigRepository;
    @Autowired private AuditLogService auditLogService;

    // ---------- GET /api/engine-rooms/{plant} ----------
    @GetMapping("/api/engine-rooms/{plant}")
    public List<PumpDTO> getEngineRooms(@PathVariable String plant) {
        return buildPumpDTOs(plant);
    }

    @GetMapping("/api/pumps/{plant}")
    public List<PumpDTO> getPumps(@PathVariable String plant) {
        return buildPumpDTOs(plant);
    }

    private List<PumpDTO> buildPumpDTOs(String plant) {
        List<PumpConfig> configs = pumpConfigRepository.findByPlantName(plant);
        List<PumpState> states = pumpStateRepository.findByPlantName(plant);
        Map<Integer, PumpState> stateByNumber = states.stream()
            .collect(Collectors.toMap(PumpState::getPumpNumber, s -> s, (a, b) -> a));

        List<PumpDTO> result = new ArrayList<>();
        for (PumpConfig cfg : configs) {
            PumpState st = stateByNumber.get(cfg.getPumpNumber());
            PumpDTO dto = new PumpDTO();
            dto.setId(st != null ? st.getId() : null);
            dto.setNumber(cfg.getPumpNumber());
            dto.setEngineRoom("ER" + cfg.getErRoom());
            dto.setStatus(st != null ? st.getStatus() : "Standby");
            dto.setFlowRate(cfg.getFlowCapacity());
            dto.setRunningTime(formatRunningTime(st != null ? st.getAccumulatedSeconds() : 0L));
            dto.setDestination(st != null ? st.getDestination() : "Whiteridge");
            result.add(dto);
        }
        return result;
    }

    private String formatRunningTime(Long secs) {
        if (secs == null) secs = 0L;
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return String.format("%dh %dm %ds", h, m, s);
    }

    // ---------- GENERIC STATUS CHANGE (start/stop/trip/maintenance) ----------
    @PutMapping("/api/pumps/{pumpId}/status")
    public ResponseEntity<?> setStatus(@PathVariable Long pumpId,
                                       @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        String plantHint = body.getOrDefault("plant", null);
        if (newStatus == null || newStatus.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "status required"));

        Optional<PumpState> opt = pumpStateRepository.findById(pumpId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        PumpState pump = opt.get();
        String oldStatus = pump.getStatus();

        // Accumulate running time on stop
        if ("Running".equals(oldStatus) && !"Running".equals(newStatus)) {
            if (pump.getStartTimestamp() != null) {
                long elapsed = java.time.Duration.between(pump.getStartTimestamp(), LocalDateTime.now()).getSeconds();
                pump.setAccumulatedSeconds((pump.getAccumulatedSeconds() == null ? 0 : pump.getAccumulatedSeconds()) + elapsed);
            }
            pump.setStartTimestamp(null);
        }
        // Set start time on transition to Running
        if (!"Running".equals(oldStatus) && "Running".equals(newStatus)) {
            pump.setStartTimestamp(LocalDateTime.now());
        }

        pump.setStatus(newStatus);
        pumpStateRepository.save(pump);

        // Write audit log
        try {
            String eventType = "PUMP_" + newStatus.toUpperCase();
            if ("Tripped".equals(newStatus)) eventType = "STATUS_CHANGE";
            if ("Maintenance".equals(newStatus)) eventType = "MAINTENANCE";
            String details = String.format("Pump %d changed %s → %s",
                pump.getPumpNumber(), oldStatus, newStatus);
            auditLogService.log("admin", pump.getPlantName(), eventType, details, "api");
        } catch (Exception e) {
            System.err.println("Audit log write failed: " + e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
            "id", pump.getId(),
            "status", pump.getStatus(),
            "plant", pump.getPlantName(),
            "pumpNumber", pump.getPumpNumber()
        ));
    }

    // ---------- Legacy endpoints (kept for compatibility) ----------
    @PostMapping("/api/pumps/{pumpId}/start")
    public ResponseEntity<?> startPump(@PathVariable Long pumpId) {
        return setStatus(pumpId, Map.of("status", "Running"));
    }

    @PostMapping("/api/pumps/{pumpId}/stop")
    public ResponseEntity<?> stopPump(@PathVariable Long pumpId) {
        return setStatus(pumpId, Map.of("status", "Standby"));
    }

    // ---------- DESTINATION ----------
    @PutMapping("/api/pumps/{pumpId}/destination")
    public ResponseEntity<?> setDestination(@PathVariable Long pumpId,
                                            @RequestBody Map<String, String> body) {
        String destination = body.get("destination");
        if (destination == null || destination.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "destination required"));

        Optional<PumpState> opt = pumpStateRepository.findById(pumpId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        PumpState pump = opt.get();
        String oldDest = pump.getDestination();
        pump.setDestination(destination);
        pumpStateRepository.save(pump);

        try {
            auditLogService.log("admin", pump.getPlantName(), "DESTINATION",
                String.format("Pump %d destination %s → %s", pump.getPumpNumber(), oldDest, destination), "api");
        } catch (Exception e) { /* ignore */ }

        return ResponseEntity.ok(Map.of(
            "id", pump.getId(),
            "destination", pump.getDestination()
        ));
    }
}
