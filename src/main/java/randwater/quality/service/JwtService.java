package randwater.quality.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import randwater.quality.entity.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    @Value("${app.jwt.secret:RandWaterSecretKey2026ForJWT_SecureLongerThan32Bytes_OK}")
    private String secret;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long expirationMs;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("fullName", user.getFullName());
        if (user.getRole() != null) {
            claims.put("role", user.getRole().getRoleName());
            claims.put("roleId", user.getRole().getRoleId());
        }
        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getKey())
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractRole(String token) {
        Object r = extractClaims(token).get("role");
        return r != null ? r.toString() : null;
    }

    public Integer extractUserId(String token) {
        Object id = extractClaims(token).get("userId");
        if (id == null) return null;
        if (id instanceof Integer) return (Integer) id;
        return Integer.parseInt(id.toString());
    }
}
