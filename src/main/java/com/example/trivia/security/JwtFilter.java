package com.example.trivia.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@Component
@Order(1)
public class JwtFilter extends OncePerRequestFilter {
    private final JwtKeyLocator jwtKeyLocator;

    public JwtFilter(JwtKeyLocator jwtKeyLocator) {
        this.jwtKeyLocator = jwtKeyLocator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String jwt = header.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .keyLocator(this.jwtKeyLocator)
                        .build()
                        .parseSignedClaims(jwt)
                        .getPayload();

                request.setAttribute("playerId", Long.parseLong(claims.getSubject()));
            } catch (JwtException e) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.getWriter().write("Unauthorized: " + e.getMessage());
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
