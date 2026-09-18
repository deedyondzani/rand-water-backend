package randwater.quality.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import randwater.quality.dto.DashboardResponse;
import randwater.quality.service.DashboardService;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/{plantName}")
    public DashboardResponse getDashboard(@PathVariable String plantName) {
        return dashboardService.getDashboardData(plantName);
    }
}
