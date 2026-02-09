package com.example.trivia.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

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

                Long playerId = Long.parseLong(claims.getSubject());
                request.setAttribute("playerId", playerId);
                System.out.printf("playerId: %d", playerId);
            } catch (JwtException e) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.getWriter().write("Unauthorized: " + e.getMessage());
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
