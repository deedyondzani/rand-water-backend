package randwater.quality.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import randwater.quality.entity.AuditLog;
import randwater.quality.repository.AuditLogRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public List<AuditLog> getAll() {
        return auditLogRepository.findTop100ByOrderByEventTimestampDesc();
    }

    public Page<AuditLog> getFiltered(String plant, String username, String eventType,
                                      LocalDateTime fromTs, LocalDateTime toTs,
                                      List<String> excludeTypes,
                                      int page, int size) {
        String excludeCsv = (excludeTypes == null || excludeTypes.isEmpty())
            ? null
            : String.join(",", excludeTypes);
        Pageable pageable = PageRequest.of(page, size);
        return auditLogRepository.findWithFilters(plant, username, eventType,
            fromTs, toTs, excludeCsv, pageable);
    }

    public AuditLog log(String username, String plantName, String eventType,
                        String details, String ipAddress) {
        AuditLog entry = new AuditLog();
        entry.setUsername(username);
        entry.setPlantName(plantName);
        entry.setEventType(eventType);
        entry.setDetails(details);
        entry.setIpAddress(ipAddress);
        return auditLogRepository.save(entry);
    }
}
