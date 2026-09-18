package randwater.quality.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "operator_plant_rights")
@Data
@NoArgsConstructor
public class OperatorPlantRight {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "plant_name", nullable = false, length = 50)
    private String plantName;

    @Column(length = 20)
    private String status = "pending";

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private Integer reviewedBy;

    @PrePersist
    public void prePersist() {
        if (requestedAt == null) requestedAt = LocalDateTime.now();
    }
}
