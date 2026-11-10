package com.health.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokens {
  private final SecretKey key;
  public final long ttl;

  public JwtTokens(
      @Value("${app.jwt-secret}") String secret, @Value("${app.jwt-ttl-seconds}") long ttl) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || ttl <= 0)
      throw new IllegalArgumentException("JWT_SECRET须至少32字节，JWT_TTL_SECONDS须为正数");
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.ttl = ttl;
  }

  public String issue(Long id, Instant expires) {
    return Jwts.builder()
        .subject(id.toString())
        .issuedAt(new Date())
        .expiration(Date.from(expires))
        .signWith(key)
        .compact();
  }

  public Long subject(String token) {
    return Long.valueOf(
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject());
  }
}
