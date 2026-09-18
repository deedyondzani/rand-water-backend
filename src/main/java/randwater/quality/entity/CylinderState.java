package randwater.quality.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cylinder_states")
@Data
@NoArgsConstructor
public class CylinderState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "plant_name", nullable = false, length = 50)
    private String plantName;

    @Column(name = "room_number", nullable = false)
    private Integer roomNumber;

    @Column(name = "slot_number", nullable = false)
    private Integer slotNumber;

    @Column(length = 20)
    private String status = "EMPTY";

    @Version
    private Long version = 0L;
}
