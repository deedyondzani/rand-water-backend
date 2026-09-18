package randwater.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActiveUserDTO {
    private Integer userId;
    private String username;
    private String fullName;
    private String roleName;
    private Boolean isOnline;
    private LocalDateTime lastLogin;
    private LocalDateTime lastActive;
}
