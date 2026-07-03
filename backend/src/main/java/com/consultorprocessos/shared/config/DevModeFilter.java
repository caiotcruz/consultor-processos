package com.consultorprocessos.shared.config;

import com.consultorprocessos.auth.security.UserDetailsImpl;
import com.consultorprocessos.auth.security.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevModeFilter extends OncePerRequestFilter {

    @Value("${app.dev-mode.auto-login:false}")
    private boolean autoLogin;

    @Value("${app.dev-mode.auto-user-email:dev@consultorprocessos.com.br}")
    private String devUserEmail;

    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        if (autoLogin && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetailsImpl userDetails =
                        (UserDetailsImpl) userDetailsService.loadUserByUsername(devUserEmail);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("[DEV] Autenticação automática: {}", devUserEmail);

            } catch (Exception e) {
                log.warn("[DEV] Não foi possível autenticar automaticamente '{}': {}. " +
                         "Execute as migrations e o DevDataInitializer.", devUserEmail, e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }
}