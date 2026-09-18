package randwater.quality.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import randwater.quality.service.DosingChartService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dosing-chart")
@CrossOrigin(origins = "*")
public class DosingChartController {

    @Autowired
    private DosingChartService svc;

    @GetMapping("/chlorine")
    public ResponseEntity<?> chlorine(@RequestParam int flow, @RequestParam double head) {
        if (flow < 10 || flow > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "flow must be 10..1000"));
        }
        if (head < 0.2 || head > 2.4) {
            return ResponseEntity.badRequest().body(Map.of("error", "head must be 0.2..2.4"));
        }
        int rounded = svc.roundFlow(flow);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("flow", rounded);
        out.put("head", Math.round(head * 10) / 10.0);
        out.put("dose", svc.getChlorineDose(flow, head));
        out.put("formula", String.format("%d x %.1f x 0.04167", rounded, Math.round(head * 10) / 10.0));
        return ResponseEntity.ok(out);
    }

    @GetMapping("/ammonia")
    public ResponseEntity<?> ammonia(@RequestParam int flow) {
        if (flow < 10 || flow > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "flow must be 10..1000"));
        }
        int rounded = svc.roundFlow(flow);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("flow", rounded);
        out.put("dose", svc.getAmmoniaDose(flow));
        return ResponseEntity.ok(out);
    }

    @GetMapping("/chlorine/column")
    public ResponseEntity<?> chlorineColumn(@RequestParam(defaultValue = "1.2") double head) {
        if (head < 0.2 || head > 2.4) {
            return ResponseEntity.badRequest().body(Map.of("error", "head must be 0.2..2.4"));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> e : svc.getChlorineColumn(head).entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("flow", e.getKey());
            row.put("dose", e.getValue());
            rows.add(row);
        }
        return ResponseEntity.ok(Map.of(
            "head", Math.round(head * 10) / 10.0,
            "rows", rows
        ));
    }

    @GetMapping("/ammonia/table")
    public ResponseEntity<?> ammoniaTable() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> e : svc.getFullAmmoniaChart().entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("flow", e.getKey());
            row.put("dose", e.getValue());
            rows.add(row);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/meta")
    public ResponseEntity<?> meta() {
        return ResponseEntity.ok(Map.of(
            "flows", svc.getFlows(),
            "heads", svc.getHeads()
        ));
    }
}
