package randwater.quality.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randwater.quality.entity.PumpConfig;
import java.util.List;

public interface PumpConfigRepository extends JpaRepository<PumpConfig, Long> {
    List<PumpConfig> findByPlantName(String plantName);
    PumpConfig findByPlantNameAndPumpNumber(String plantName, Integer pumpNumber);
}
