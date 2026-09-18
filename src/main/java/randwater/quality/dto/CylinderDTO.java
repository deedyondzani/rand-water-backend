package randwater.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CylinderDTO {
    private Integer id;
    private String plantName;
    private Integer roomNumber;
    private Integer slotNumber;
    private String status;
}
