package randwater.quality.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import randwater.quality.entity.SystemHealth;
import java.util.List;

public interface SystemHealthRepository extends JpaRepository<SystemHealth, Long> {
    List<SystemHealth> findTop50ByOrderByIdDesc();
    Page<SystemHealth> findAllByOrderByIdDesc(Pageable pageable);
    long count();
}
