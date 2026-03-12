package com.danmaku.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    // ====================== 配置 ======================
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expire: 3600000}")    // 1小时
    private long accessExpire;

    @Value("${jwt.refresh-expire: 604800000}") // 7天
    private long refreshExpire;

    // ====================== 密钥 ======================
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // ====================== 1. 生成 ACCESS TOKEN ======================
    public String generateAccessToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "access");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpire))
                .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ====================== 2. 生成 REFRESH TOKEN ======================
    public String generateRefreshToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpire))
                .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ====================== 3. 从 token 中获取 userId ======================
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSecretKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("userId", Long.class);
    }

    // ====================== 4. 验证 token 是否有效 ======================
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSecretKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ====================== 5. 判断是否是 AccessToken ======================
    public boolean isAccessToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSecretKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return "access".equals(claims.get("type"));
    }

    // ====================== 6. 判断是否是 RefreshToken ======================
    public boolean isRefreshToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSecretKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return "refresh".equals(claims.get("type"));
    }
}