package randwater.quality.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randwater.quality.entity.TankState;
import java.util.List;

public interface TankStateRepository extends JpaRepository<TankState, Integer> {
    List<TankState> findByPlantNameOrderByTankNumberAsc(String plantName);
}
