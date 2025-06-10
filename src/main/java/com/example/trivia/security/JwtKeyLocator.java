package com.example.trivia.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;

import javax.crypto.SecretKey;

import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtKeyLocator extends LocatorAdapter<Key> {
    private final SecretKey key;

    public JwtKeyLocator(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Key locate(JwsHeader header) {
        // Use a single key for all signatures
        return this.key;
    }
}
