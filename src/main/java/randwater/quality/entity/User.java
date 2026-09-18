package randwater.quality.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    // NOTE: plain text for now — bcrypt migration planned
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "password_reset_required")
    private Boolean passwordResetRequired = false;

    @Column(name = "is_online")
    private Boolean isOnline = false;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "last_active")
    private LocalDateTime lastActive;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
