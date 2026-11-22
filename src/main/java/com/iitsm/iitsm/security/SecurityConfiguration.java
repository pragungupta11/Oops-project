package com.iitsm.iitsm.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF protection ON: every form gets a hidden token automatically (th:action).
                // Only the H2 console (admin-only, development tool) is excluded.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/error").permitAll()
                        .requestMatchers("/h2-console/**").hasRole("ADMIN")
                        .requestMatchers("/employee/**").hasRole("EMPLOYEE")
                        .requestMatchers("/technician/**").hasRole("TECHNICIAN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(Customizer.withDefaults())
                .exceptionHandling(ex -> ex.accessDeniedHandler(staleFormHandler()))
                .build();
    }

    /**
     * A form whose CSRF token has expired (page left open across a restart or a session timeout)
     * is sent back to the login page with a message instead of the generic "no access" page.
     * Every other access-denied case keeps the normal 403 page.
     */
    private AccessDeniedHandler staleFormHandler() {
        AccessDeniedHandler fallback = new AccessDeniedHandlerImpl();
        return (request, response, exception) -> {
            if (exception instanceof CsrfException) {
                response.sendRedirect(request.getContextPath() + "/login?expired");
            } else {
                fallback.handle(request, response, exception);
            }
        };
    }
}
