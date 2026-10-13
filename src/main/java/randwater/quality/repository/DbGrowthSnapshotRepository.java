package randwater.quality.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randwater.quality.entity.DbGrowthSnapshot;

import java.time.LocalDateTime;
import java.util.List;

public interface DbGrowthSnapshotRepository extends JpaRepository<DbGrowthSnapshot, Long> {
    List<DbGrowthSnapshot> findTop90ByOrderByCapturedAtDesc();
    List<DbGrowthSnapshot> findTop365ByOrderByCapturedAtDesc();
    List<DbGrowthSnapshot> findByCapturedAtBefore(LocalDateTime cutoff);
}
