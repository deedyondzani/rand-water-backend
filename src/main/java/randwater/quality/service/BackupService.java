package randwater.quality.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import randwater.quality.entity.BackupSnapshot;
import randwater.quality.repository.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BackupService {

    private final BackupSnapshotRepository backupRepo;
    private final PumpStateRepository pumpRepo;
    private final CylinderStateRepository cylRepo;
    private final TankStateRepository tankRepo;
    private final PumpConfigRepository pumpCfgRepo;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedRate = 300000, initialDelay = 60000) // every 5 minutes
    public void autoBackup() {
        createBackup("AUTO", "scheduled");
    }

    public BackupSnapshot createBackup(String type, String notes) {
        BackupSnapshot snap = new BackupSnapshot();
        snap.setType(type);
        snap.setNotes(notes);
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pump_states", pumpRepo.findAll());
            data.put("cylinder_states", cylRepo.findAll());
            data.put("tank_states", tankRepo.findAll());
            data.put("pump_config", pumpCfgRepo.findAll());

            String json = objectMapper.writeValueAsString(data);
            snap.setDataJson(json);
            snap.setSizeBytes((long) json.length());
            snap.setStatus("OK");
        } catch (Exception e) {
            snap.setStatus("FAILED");
            snap.setNotes((notes != null ? notes + " | " : "") + "Error: " + e.getMessage());
        }
        BackupSnapshot saved = backupRepo.save(snap);
        prune();
        return saved;
    }

    private void prune() {
        List<BackupSnapshot> all = backupRepo.findTop20ByOrderByIdDesc();
        if (all.size() > 20) {
            for (int i = 20; i < all.size(); i++) {
                backupRepo.delete(all.get(i));
            }
        }
    }

    public List<BackupSnapshot> recent() {
        return backupRepo.findTop20ByOrderByIdDesc();
    }

    public BackupSnapshot get(Long id) {
        return backupRepo.findById(id).orElse(null);
    }
}
