package randwater.quality.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bulk_storage")
@Data
@NoArgsConstructor
public class BulkStorage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "plant_name", nullable = false, unique = true, length = 50)
    private String plantName;

    @Column(name = "full_count")
    private Integer fullCount = 0;

    @Column(name = "empty_count")
    private Integer emptyCount = 0;

    @Column(name = "oc_count")
    private Integer ocCount = 0;

    @Version
    private Long version = 0L;
}
