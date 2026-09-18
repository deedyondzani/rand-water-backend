package randwater.quality.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randwater.quality.entity.OperatorPlantRight;
import java.util.List;

public interface OperatorPlantRightRepository extends JpaRepository<OperatorPlantRight, Integer> {
    List<OperatorPlantRight> findByUserId(Integer userId);
    List<OperatorPlantRight> findByStatusOrderByRequestedAtAsc(String status);
    void deleteByUserId(Integer userId);
}
