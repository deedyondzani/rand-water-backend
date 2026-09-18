package randwater.quality.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randwater.quality.entity.BackupSnapshot;
import java.util.List;

public interface BackupSnapshotRepository extends JpaRepository<BackupSnapshot, Long> {
    List<BackupSnapshot> findTop20ByOrderByIdDesc();
}
