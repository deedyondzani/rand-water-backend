package randwater.quality.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import randwater.quality.dto.DashboardResponse;
import randwater.quality.repository.PumpStateRepository;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PumpStateRepository pumpStateRepository;

    private static final Map<String, Integer> DESIGN_CAPACITY = Map.of(
        "Palmiet", 2050,
        "Eikenhof", 1200,
        "Zwartkopjes", 700
    );

    private static final Double CL2_TARGET = 42.27;
    private static final Double NH3_TARGET = 73.17;
    private static final Double ALARM_THRESHOLD = 105.0;

    public DashboardResponse getDashboardData(String plantName) {
        Integer totalFlow = pumpStateRepository.sumRunningFlowByPlant(plantName);
        if (totalFlow == null) totalFlow = 0;

        Long runningPumps = pumpStateRepository.countRunningPumps(plantName);
        Long totalPumps = pumpStateRepository.countTotalPumps(plantName);

        Integer designCap = DESIGN_CAPACITY.getOrDefault(plantName, 2050);
        Double loadPct = (designCap > 0) ? (totalFlow.doubleValue() / designCap) * 100.0 : 0.0;

        Double cl2Dosing = totalFlow * (CL2_TARGET / 1000.0);
        Double nh3Dosing = totalFlow * (NH3_TARGET / 1000.0);

        String alarmStatus = (loadPct > ALARM_THRESHOLD) ? "CRITICAL" : "NORMAL";

        return new DashboardResponse(
            plantName,
            totalFlow,
            runningPumps,
            totalPumps,
            Math.round(loadPct * 10.0) / 10.0,
            Math.round(cl2Dosing * 10.0) / 10.0,
            Math.round(nh3Dosing * 10.0) / 10.0,
            alarmStatus
        );
    }
}
