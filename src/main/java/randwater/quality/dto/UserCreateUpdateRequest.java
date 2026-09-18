package randwater.quality.dto;

import lombok.Data;
import java.util.List;

@Data
public class UserCreateUpdateRequest {
    private String username;
    private String password;        // optional on update
    private String fullName;
    private String roleName;        // 'admin' | 'supervisor' | 'operator'
    private Boolean isActive;
    private List<PlantRightDTO> plantRights;  // for operators only
}
