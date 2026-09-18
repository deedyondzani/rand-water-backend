package randwater.quality.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randwater.quality.entity.CylinderState;
import java.util.List;

public interface CylinderStateRepository extends JpaRepository<CylinderState, Integer> {
    List<CylinderState> findByPlantNameOrderByRoomNumberAscSlotNumberAsc(String plantName);
}
