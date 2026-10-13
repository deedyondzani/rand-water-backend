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

    @Scheduled(fixedRate = 3600000, initialDelay = 60000) // every 1 hour
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

    private static final int KEEP_SNAPSHOTS = 500;

    private void prune() {
        List<BackupSnapshot> all = backupRepo.findAllByOrderByIdDesc();
        if (all.size() > KEEP_SNAPSHOTS) {
            for (int i = KEEP_SNAPSHOTS; i < all.size(); i++) {
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
