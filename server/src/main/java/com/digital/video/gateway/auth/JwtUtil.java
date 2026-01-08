package com.digital.video.gateway.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * JWT工具类
 */
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    private static final String SECRET = "hikvision-nvr-secret-key-2024"; // 生产环境应该从配置文件读取
    private static final String ISSUER = "hikvision-nvr";
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24小时

    /**
     * 生成JWT token
     */
    public static String generateToken(String username) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(SECRET);
            Date expiresAt = new Date(System.currentTimeMillis() + EXPIRATION_TIME);
            
            String token = JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(username)
                    .withExpiresAt(expiresAt)
                    .withIssuedAt(new Date())
                    .sign(algorithm);
            
            return token;
        } catch (JWTCreationException e) {
            logger.error("生成JWT token失败", e);
            return null;
        }
    }

    /**
     * 验证JWT token
     */
    public static String verifyToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(SECRET);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build();
            
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getSubject();
        } catch (JWTVerificationException e) {
            logger.debug("JWT token验证失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取token过期时间（秒）
     */
    public static long getExpirationTime() {
        return EXPIRATION_TIME / 1000;
    }
}
