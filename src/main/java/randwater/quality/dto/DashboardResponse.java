package randwater.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardResponse {
    private String plantName;
    private Integer totalFlow;
    private Long runningPumps;
    private Long totalPumps;
    private Double loadPercentage;
    private Double cl2Dosing;
    private Double nh3Dosing;
    private String alarmStatus;
}
