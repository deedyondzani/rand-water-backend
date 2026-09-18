package randwater.quality.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import randwater.quality.entity.SystemHealth;
import randwater.quality.repository.SystemHealthRepository;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HealthMonitorService {

    private final SystemHealthRepository healthRepo;
    private final DataSource dataSource;

    private static final int KEEP_ROWS = 500;

    @Scheduled(fixedRate = 30000, initialDelay = 15000)
    public void sample() {
        SystemHealth h = new SystemHealth();

        // ---- DB check ----
        long t0 = System.currentTimeMillis();
        boolean dbOk = false;
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            s.executeQuery("SELECT 1");
            dbOk = true;
        } catch (Exception e) {
            h.setNotes("DB error: " + e.getMessage());
        }
        int dbLatency = (int) (System.currentTimeMillis() - t0);
        h.setDbOk(dbOk);
        h.setDbLatencyMs(dbLatency);

        // ---- JVM ----
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        long usedMb = mem.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long maxMb = mem.getHeapMemoryUsage().getMax() / (1024 * 1024);
        h.setHeapUsedMb((int) usedMb);
        h.setHeapMaxMb((int) maxMb);

        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        h.setCpuLoad(os.getSystemLoadAverage());

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        h.setActiveThreads(threads.getThreadCount());

        h.setUptimeSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000);

        // ---- Status decision ----
        String status = "HEALTHY";
        if (!dbOk) status = "CRITICAL";
        else if (dbLatency > 2000) status = "DEGRADED";
        else if (maxMb > 0 && usedMb * 100 / maxMb > 90) status = "DEGRADED";
        h.setStatus(status);

        healthRepo.save(h);

        // ---- Retention: keep only newest N ----
        long total = healthRepo.count();
        if (total > KEEP_ROWS + 100) {
            healthRepo.findAllByOrderByIdDesc(PageRequest.of(0, (int) (total - KEEP_ROWS)))
                .forEach(healthRepo::delete);
        }
    }

    public List<SystemHealth> recent() {
        return healthRepo.findTop50ByOrderByIdDesc();
    }

    public SystemHealth latest() {
        List<SystemHealth> r = healthRepo.findTop50ByOrderByIdDesc();
        return r.isEmpty() ? null : r.get(0);
    }
}
