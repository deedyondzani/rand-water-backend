package randwater.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PumpDTO {
    private Long id;
    private Integer number;
    private String engineRoom;
    private String status;
    private Integer flowRate;
    private String runningTime;
    private String destination;
}
