package randwater.quality.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Data
@NoArgsConstructor
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @Column(name = "event_timestamp", insertable = false, updatable = false)
    private LocalDateTime eventTimestamp;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "plant_name", length = 50)
    private String plantName;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @Column(name = "details", columnDefinition = "text")
    private String details;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;
}
