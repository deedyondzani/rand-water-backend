package randwater.quality.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "pump_states")
@Data
@NoArgsConstructor
public class PumpState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_name", nullable = false)
    private String plantName;

    @Column(name = "pump_number", nullable = false)
    private Integer pumpNumber;

    @Column(nullable = false)
    private String status;

    @Column(name = "start_timestamp")
    private LocalDateTime startTimestamp;

    @Column(name = "accumulated_seconds")
    private Long accumulatedSeconds = 0L;

    @Column(name = "destination")
    private String destination = "Whiteridge";

    @Version
    private Long version = 0L;
}
