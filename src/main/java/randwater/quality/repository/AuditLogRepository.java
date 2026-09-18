package randwater.quality.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import randwater.quality.entity.AuditLog;
import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {

    @Query(value = "SELECT * FROM audit_log WHERE " +
           "(CAST(:plant AS text) IS NULL OR plant_name = CAST(:plant AS text) " +
           "  OR plant_name IS NULL) AND " +
           "(CAST(:username AS text) IS NULL OR username = CAST(:username AS text)) AND " +
           "(CAST(:eventType AS text) IS NULL OR event_type = CAST(:eventType AS text)) AND " +
           "(CAST(:fromTs AS timestamp) IS NULL OR event_timestamp >= CAST(:fromTs AS timestamp)) AND " +
           "(CAST(:toTs AS timestamp) IS NULL OR event_timestamp <= CAST(:toTs AS timestamp)) AND " +
           "(CAST(:excludeTypes AS text) IS NULL OR event_type != ALL(string_to_array(CAST(:excludeTypes AS text), ','))) " +
           "ORDER BY event_timestamp DESC",
           countQuery = "SELECT COUNT(*) FROM audit_log WHERE " +
           "(CAST(:plant AS text) IS NULL OR plant_name = CAST(:plant AS text) " +
           "  OR plant_name IS NULL) AND " +
           "(CAST(:username AS text) IS NULL OR username = CAST(:username AS text)) AND " +
           "(CAST(:eventType AS text) IS NULL OR event_type = CAST(:eventType AS text)) AND " +
           "(CAST(:fromTs AS timestamp) IS NULL OR event_timestamp >= CAST(:fromTs AS timestamp)) AND " +
           "(CAST(:toTs AS timestamp) IS NULL OR event_timestamp <= CAST(:toTs AS timestamp)) AND " +
           "(CAST(:excludeTypes AS text) IS NULL OR event_type != ALL(string_to_array(CAST(:excludeTypes AS text), ',')))",
           nativeQuery = true)
    Page<AuditLog> findWithFilters(
        @Param("plant") String plant,
        @Param("username") String username,
        @Param("eventType") String eventType,
        @Param("fromTs") LocalDateTime fromTs,
        @Param("toTs") LocalDateTime toTs,
        @Param("excludeTypes") String excludeTypes,
        Pageable pageable
    );

    List<AuditLog> findTop100ByOrderByEventTimestampDesc();
}
