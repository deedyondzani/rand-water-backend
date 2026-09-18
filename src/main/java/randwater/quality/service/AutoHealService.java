package randwater.quality.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import randwater.quality.entity.CylinderState;
import randwater.quality.entity.PumpState;
import randwater.quality.entity.TankState;
import randwater.quality.repository.*;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AutoHealService {

    private final PumpStateRepository pumpRepo;
    private final CylinderStateRepository cylRepo;
    private final TankStateRepository tankRepo;
    private final AuditLogService auditLogService;

    private static final Set<String> PUMP_OK = Set.of("Running", "Standby", "Tripped", "Maintenance");
    private static final Set<String> CYL_OK = Set.of("FULL", "IN_USE", "EMPTY", "O/C");
    private static final Set<String> TANK_OK = Set.of("In Use", "Standby", "Empty", "Maintenance");

    // Max tank level per plant (in litres)
    private int maxTankLevel(String plant) {
        if (plant == null) return 14000;
        return plant.equalsIgnoreCase("Palmiet") ? 34500 : 14000;
    }

    public Map<String, Object> runHeal() {
        Map<String, Object> report = new LinkedHashMap<>();
        List<String> fixes = new ArrayList<>();

        // ---- 1. Pump invariant: if Running but start_timestamp is null → set now
        for (PumpState p : pumpRepo.findAll()) {
            boolean changed = false;
            if (!PUMP_OK.contains(p.getStatus())) {
                fixes.add(String.format("Pump %s #%d: invalid status '%s' → Standby",
                    p.getPlantName(), p.getPumpNumber(), p.getStatus()));
                p.setStatus("Standby");
                changed = true;
            }
            if ("Running".equals(p.getStatus()) && p.getStartTimestamp() == null) {
                p.setStartTimestamp(LocalDateTime.now());
                fixes.add(String.format("Pump %s #%d: missing start time → set now",
                    p.getPlantName(), p.getPumpNumber()));
                changed = true;
            }
            if (!"Running".equals(p.getStatus()) && p.getStartTimestamp() != null) {
                p.setStartTimestamp(null);
                changed = true;
            }
            if (changed) pumpRepo.save(p);
        }

        // ---- 2. Cylinder invariant
        for (CylinderState c : cylRepo.findAll()) {
            if (!CYL_OK.contains(c.getStatus())) {
                fixes.add(String.format("Cylinder %s room %d slot %d: '%s' → EMPTY",
                    c.getPlantName(), c.getRoomNumber(), c.getSlotNumber(), c.getStatus()));
                c.setStatus("EMPTY");
                cylRepo.save(c);
            }
        }

        // ---- 3. Tank invariant: level out of range or invalid status
        for (TankState t : tankRepo.findAll()) {
            boolean changed = false;
            int max = maxTankLevel(t.getPlantName());
            if (t.getLevel() == null || t.getLevel() < 0) {
                fixes.add(String.format("Tank %s #%d: level < 0 → 0", t.getPlantName(), t.getTankNumber()));
                t.setLevel(0);
                changed = true;
            } else if (t.getLevel() > max) {
                fixes.add(String.format("Tank %s #%d: level %d > max %d → clamp",
                    t.getPlantName(), t.getTankNumber(), t.getLevel(), max));
                t.setLevel(max);
                changed = true;
            }
            if (!TANK_OK.contains(t.getStatus())) {
                fixes.add(String.format("Tank %s #%d: invalid status '%s' → Standby",
                    t.getPlantName(), t.getTankNumber(), t.getStatus()));
                t.setStatus("Standby");
                changed = true;
            }
            if (changed) tankRepo.save(t);
        }

        // ---- Log result ----
        if (!fixes.isEmpty()) {
            try {
                auditLogService.log("JOE", null, "JOE_HEAL",
                    "Auto-heal applied " + fixes.size() + " fix(es): " + String.join("; ", fixes),
                    "joe");
            } catch (Exception ignored) {}
        }

        report.put("fixesApplied", fixes.size());
        report.put("fixes", fixes);
        report.put("ranAt", LocalDateTime.now().toString());
        return report;
    }
}
