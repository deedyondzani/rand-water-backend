package randwater.quality.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randwater.quality.entity.BulkStorage;
import java.util.Optional;

public interface BulkStorageRepository extends JpaRepository<BulkStorage, Integer> {
    Optional<BulkStorage> findByPlantName(String plantName);
}
