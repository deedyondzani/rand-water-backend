package randwater.quality.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pump_config")
@Data
@NoArgsConstructor
public class PumpConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_name", nullable = false)
    private String plantName;

    @Column(name = "er_room", nullable = false)
    private Integer erRoom;

    @Column(name = "pump_number", nullable = false)
    private Integer pumpNumber;

    @Column(name = "flow_capacity", nullable = false)
    private Integer flowCapacity;
}
