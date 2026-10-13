package randwater.quality.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "db_growth_snapshot")
@Data
@NoArgsConstructor
public class DbGrowthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "captured_at", insertable = false, updatable = false)
    private LocalDateTime capturedAt;

    @Column(name = "audit_log_rows")        private Long auditLogRows;
    @Column(name = "audit_log_bytes")       private Long auditLogBytes;
    @Column(name = "total_db_bytes")        private Long totalDbBytes;
    @Column(name = "user_count")            private Integer userCount;
    @Column(name = "pump_state_count")      private Integer pumpStateCount;
    @Column(name = "cylinder_state_count")  private Integer cylinderStateCount;
    @Column(name = "tank_state_count")      private Integer tankStateCount;
    @Column(name = "backup_count")          private Integer backupCount;
    @Column(name = "backup_disk_bytes")     private Long backupDiskBytes;
    @Column(columnDefinition = "text")      private String notes;
}
