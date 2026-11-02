package com.hotel.hotelmanagement.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads "Authorization: Bearer <token>", verifies it, and marks the request
 * as logged in. Requests without a valid token stay anonymous.
 *
 * Created by SecurityConfig (not a @Component) so it runs only once.
 */
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final TokenService tokenService;

    public TokenAuthFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            tokenService.verify(header.substring(PREFIX.length()).trim())
                    .ifPresent(user -> {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        user,
                                        null,
                                        List.of(new SimpleGrantedAuthority(
                                                "ROLE_" + user.role())));

                        SecurityContextHolder.getContext()
                                .setAuthentication(authentication);
                    });
        }

        filterChain.doFilter(request, response);
    }
}
