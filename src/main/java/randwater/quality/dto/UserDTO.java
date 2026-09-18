package randwater.quality.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Integer userId;
    private String username;
    private String fullName;
    private String roleName;
    private Integer roleId;
    private Boolean isActive;
    private Boolean passwordResetRequired;
    private Boolean isOnline;
    private LocalDateTime lastLogin;
    private LocalDateTime lastActive;
    private List<PlantRightDTO> plantRights;
}
