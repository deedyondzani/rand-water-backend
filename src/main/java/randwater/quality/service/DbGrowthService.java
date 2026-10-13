package randwater.quality.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import randwater.quality.entity.DbGrowthSnapshot;
import randwater.quality.repository.*;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DbGrowthService {

    private final DataSource dataSource;
    private final DbGrowthSnapshotRepository growthRepo;
    private final UserRepository userRepo;
    private final PumpStateRepository pumpRepo;
    private final CylinderStateRepository cylRepo;
    private final TankStateRepository tankRepo;
    private final BackupSnapshotRepository backupRepo;

    private static final String BACKUP_DIR = "/home/dee_dyondzani/backups";
    private static final int KEEP_DAYS = 365;

    @Scheduled(cron = "0 0 4 * * *")
    public void dailyCapture() {
        try {
            DbGrowthSnapshot snap = capture("scheduled");
            log.info("DB growth snapshot captured: id={}, auditRows={}, totalBytes={}",
                snap.getId(), snap.getAuditLogRows(), snap.getTotalDbBytes());
        } catch (Exception e) {
            log.error("DB growth capture failed", e);
        }
    }

    public DbGrowthSnapshot capture(String note) throws Exception {
        DbGrowthSnapshot snap = new DbGrowthSnapshot();
        snap.setNotes(note);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT " +
                "  (SELECT COUNT(*) FROM audit_log) AS audit_rows, " +
                "  pg_total_relation_size('audit_log') AS audit_bytes, " +
                "  pg_database_size(current_database()) AS total_bytes"
             );
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                snap.setAuditLogRows(rs.getLong("audit_rows"));
                snap.setAuditLogBytes(rs.getLong("audit_bytes"));
                snap.setTotalDbBytes(rs.getLong("total_bytes"));
            }
        }

        snap.setUserCount((int) userRepo.count());
        snap.setPumpStateCount((int) pumpRepo.count());
        snap.setCylinderStateCount((int) cylRepo.count());
        snap.setTankStateCount((int) tankRepo.count());
        snap.setBackupCount((int) backupRepo.count());
        snap.setBackupDiskBytes(dirSize(new File(BACKUP_DIR)));

        DbGrowthSnapshot saved = growthRepo.save(snap);

        LocalDateTime cutoff = LocalDateTime.now().minusDays(KEEP_DAYS);
        List<DbGrowthSnapshot> old = growthRepo.findByCapturedAtBefore(cutoff);
        if (!old.isEmpty()) growthRepo.deleteAll(old);

        return saved;
    }

    public List<DbGrowthSnapshot> recent(int days) {
        List<DbGrowthSnapshot> all = growthRepo.findTop365ByOrderByCapturedAtDesc();
        if (days > 0 && all.size() > days) return all.subList(0, days);
        return all;
    }

    private long dirSize(File dir) {
        if (dir == null || !dir.exists()) return 0L;
        if (dir.isFile()) return dir.length();
        long total = 0L;
        File[] children = dir.listFiles();
        if (children == null) return 0L;
        for (File f : children) total += dirSize(f);
        return total;
    }
}
