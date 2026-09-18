package randwater.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TankDTO {
    private Integer id;
    private String plantName;
    private Integer tankNumber;
    private Integer level;
    private String status;
    private Integer maxLiters;
}
