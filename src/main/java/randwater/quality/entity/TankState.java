package randwater.quality.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tank_states")
@Data
@NoArgsConstructor
public class TankState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "plant_name", nullable = false, length = 50)
    private String plantName;

    @Column(name = "tank_number", nullable = false)
    private Integer tankNumber;

    @Column
    private Integer level = 0;

    @Column(length = 20)
    private String status = "Standby";

    @Version
    private Long version = 0L;
}
