package randwater.quality.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "backup_snapshot")
@Data
@NoArgsConstructor
public class BackupSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "data_json", columnDefinition = "text")
    private String dataJson;
}
