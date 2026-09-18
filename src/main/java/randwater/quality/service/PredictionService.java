package randwater.quality.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import randwater.quality.entity.PumpState;
import randwater.quality.entity.TankState;
import randwater.quality.repository.PumpStateRepository;
import randwater.quality.repository.TankStateRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PredictionService {

    private final PumpStateRepository pumpRepo;
    private final TankStateRepository tankRepo;

    // Per-plant NH3 dosing factor (L/h per ML/d), matches DashboardService
    private static final double NH3_TARGET = 73.17 / 1000.0;

    // Per-plant design capacity for flow estimation
    private static final Map<String, Integer> CAPACITY = Map.of(
        "Palmiet", 2050,
        "Eikenhof", 1200,
        "Zwartkopjes", 700
    );

    // Pump flow per plant/pump config — hardcoded for now
    private int flowForPump(String plant, int pumpNumber) {
        if ("Palmiet".equalsIgnoreCase(plant)) {
            if (pumpNumber >= 1 && pumpNumber <= 7) return 90;
            if (pumpNumber >= 8 && pumpNumber <= 12) return 180;
            if (pumpNumber == 13 || pumpNumber == 15 || pumpNumber == 17 || pumpNumber == 18) return 25;
            if (pumpNumber == 16) return 50;
            if (pumpNumber >= 19 && pumpNumber <= 26) return 200;
        }
        if ("Eikenhof".equalsIgnoreCase(plant)) {
            if (pumpNumber >= 1 && pumpNumber <= 8) return 100;
            if (pumpNumber >= 9 && pumpNumber <= 13) return 200;
            if (pumpNumber >= 14 && pumpNumber <= 17) return 200;
        }
        if ("Zwartkopjes".equalsIgnoreCase(plant)) {
            if (pumpNumber >= 1 && pumpNumber <= 4) return 100;
            if (pumpNumber >= 14 && pumpNumber <= 22) return 100;
        }
        return 0;
    }

    // ============ C2 — TANK DEPLETION ETAs ============
    public List<Map<String, Object>> tankEtas() {
        List<Map<String, Object>> result = new ArrayList<>();

        // Compute running flow per plant from pump states
        Map<String, Integer> plantFlow = new HashMap<>();
        for (PumpState p : pumpRepo.findAll()) {
            if ("Running".equals(p.getStatus())) {
                plantFlow.merge(p.getPlantName(), flowForPump(p.getPlantName(), p.getPumpNumber()), Integer::sum);
            }
        }

        for (TankState t : tankRepo.findAll()) {
            int level = t.getLevel() != null ? t.getLevel() : 0;
            int max = "Palmiet".equalsIgnoreCase(t.getPlantName()) ? 34500 : 14000;
            String status = t.getStatus();

            // Only predict depletion for In Use tanks
            if (!"In Use".equals(status) || level <= 0) continue;

            int flow = plantFlow.getOrDefault(t.getPlantName(), 0);
            double nh3RateLph = flow * NH3_TARGET;   // L/h

            if (nh3RateLph <= 0.001) continue;

            double hoursLeft = level / nh3RateLph;
            LocalDateTime depletionTime = LocalDateTime.now().plusMinutes((long) (hoursLeft * 60));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("plantName", t.getPlantName());
            row.put("tankNumber", t.getTankNumber());
            row.put("currentLevelL", level);
            row.put("maxLiters", max);
            row.put("percentFull", Math.round((double) level / max * 1000) / 10.0);
            row.put("nh3RateLph", Math.round(nh3RateLph * 100) / 100.0);
            row.put("hoursRemaining", Math.round(hoursLeft * 10) / 10.0);
            row.put("estimatedDepletion", depletionTime.toString());

            String severity;
            if (hoursLeft < 4) severity = "CRITICAL";
            else if (hoursLeft < 8) severity = "HIGH";
            else if (hoursLeft < 24) severity = "WARN";
            else severity = "OK";
            row.put("severity", severity);

            result.add(row);
        }

        result.sort((a, b) -> Double.compare(
            (double) a.get("hoursRemaining"),
            (double) b.get("hoursRemaining")));
        return result;
    }

    // ============ C3 — PUMP HEALTH SCORING (continuous-duty profile) ============
    // Pumps are designed for 24/7 operation. Running continuously is NOT a penalty.
    // Scoring focuses on: trips, maintenance state, and accumulated hours since maintenance.
    public List<Map<String, Object>> pumpHealth() {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (PumpState p : pumpRepo.findAll()) {
            int score = 100;
            List<String> reasons = new ArrayList<>();

            // 1. Continuous running >30 days may indicate rotation is due
            if ("Running".equals(p.getStatus()) && p.getStartTimestamp() != null) {
                long days = Duration.between(p.getStartTimestamp(), now).toDays();
                if (days >= 60)      { score -= 20; reasons.add("Continuously running " + days + "d (>60d)"); }
                else if (days >= 30) { score -= 10; reasons.add("Continuously running " + days + "d (>30d)"); }
            }

            // 2. Status penalties
            if ("Tripped".equals(p.getStatus())) {
                score -= 40;
                reasons.add("Tripped state");
            }
            if ("Maintenance".equals(p.getStatus())) {
                score -= 10;
                reasons.add("Under maintenance");
            }

            // 3. Accumulated runtime since last maintenance
            //    Pumps are rated for thousands of hours; these are maintenance-interval flags
            long accHours = (p.getAccumulatedSeconds() != null ? p.getAccumulatedSeconds() : 0) / 3600;
            if (accHours >= 4000)      { score -= 30; reasons.add("Runtime " + accHours + "h since maintenance (>4000h)"); }
            else if (accHours >= 2000) { score -= 15; reasons.add("Runtime " + accHours + "h since maintenance (>2000h)"); }
            else if (accHours >= 1000) { score -= 5;  reasons.add("Runtime " + accHours + "h since maintenance (>1000h)"); }

            // 4. Idle pumps (Standby >14 days) — should be rotated
            if ("Standby".equals(p.getStatus()) && accHours < 10) {
                // No start_timestamp for standby, so we can't compute idle time exactly
                // Flag only if there's evidence of long inactivity (accumulated_seconds is 0)
                // Skipped for now
            }

            // Clamp
            score = Math.max(0, Math.min(100, score));

            String grade;
            String color;
            if (score >= 80)      { grade = "HEALTHY";  color = "#28A743"; }
            else if (score >= 60) { grade = "WATCH";    color = "#FF9800"; }
            else                  { grade = "SERVICE";  color = "#d32f2f"; }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("plantName", p.getPlantName());
            row.put("pumpNumber", p.getPumpNumber());
            row.put("status", p.getStatus());
            row.put("flowCapacity", flowForPump(p.getPlantName(), p.getPumpNumber()));
            row.put("score", score);
            row.put("grade", grade);
            row.put("color", color);
            row.put("reasons", reasons);
            result.add(row);
        }

        // Sort worst first
        result.sort(Comparator.comparingInt(r -> (int) r.get("score")));
        return result;
    }

    // ============ Combined anomalies for /joe/anomalies ============
    public List<Map<String, Object>> predictionAnomalies() {
        List<Map<String, Object>> issues = new ArrayList<>();

        for (Map<String, Object> t : tankEtas()) {
            String sev = (String) t.get("severity");
            if ("OK".equals(sev)) continue;
            issues.add(Map.of(
                "severity", sev,
                "type", "TANK_LOW_ETA",
                "message", String.format("%s tank %d at %.0f%% — predicted empty in %.1fh",
                    t.get("plantName"), t.get("tankNumber"),
                    t.get("percentFull"), t.get("hoursRemaining"))
            ));
        }

        for (Map<String, Object> p : pumpHealth()) {
            int score = (int) p.get("score");
            if (score < 60) {
                issues.add(Map.of(
                    "severity", "HIGH",
                    "type", "PUMP_HEALTH_LOW",
                    "message", String.format("%s pump %d health score %d (%s)",
                        p.get("plantName"), p.get("pumpNumber"), score,
                        String.join(", ", (List<String>) p.get("reasons")))
                ));
            } else if (score < 80) {
                issues.add(Map.of(
                    "severity", "WARN",
                    "type", "PUMP_HEALTH_WATCH",
                    "message", String.format("%s pump %d health score %d (%s)",
                        p.get("plantName"), p.get("pumpNumber"), score,
                        String.join(", ", (List<String>) p.get("reasons")))
                ));
            }
        }

        return issues;
    }
}
