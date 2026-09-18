package randwater.quality.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_health")
@Data
@NoArgsConstructor
public class SystemHealth {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "captured_at", insertable = false, updatable = false)
    private LocalDateTime capturedAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "db_ok")
    private Boolean dbOk;

    @Column(name = "db_latency_ms")
    private Integer dbLatencyMs;

    @Column(name = "heap_used_mb")
    private Integer heapUsedMb;

    @Column(name = "heap_max_mb")
    private Integer heapMaxMb;

    @Column(name = "cpu_load")
    private Double cpuLoad;

    @Column(name = "active_threads")
    private Integer activeThreads;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(columnDefinition = "text")
    private String notes;
}
