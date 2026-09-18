package randwater.quality.repository;

import randwater.quality.entity.PumpState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PumpStateRepository extends JpaRepository<PumpState, Long> {
    List<PumpState> findByPlantName(String plantName);

    @Query("SELECT COALESCE(SUM(pc.flowCapacity), 0) " +
           "FROM PumpConfig pc JOIN PumpState ps " +
           "ON pc.plantName = ps.plantName AND pc.pumpNumber = ps.pumpNumber " +
           "WHERE pc.plantName = :plantName AND ps.status = 'Running'")
    Integer sumRunningFlowByPlant(@Param("plantName") String plantName);

    @Query("SELECT COUNT(ps) FROM PumpState ps WHERE ps.plantName = :plantName AND ps.status = 'Running'")
    Long countRunningPumps(@Param("plantName") String plantName);

    @Query("SELECT COUNT(pc) FROM PumpConfig pc WHERE pc.plantName = :plantName")
    Long countTotalPumps(@Param("plantName") String plantName);
}
