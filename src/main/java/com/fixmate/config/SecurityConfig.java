package com.fixmate.config;

import com.fixmate.security.JwtAuthenticationFilter;
import com.fixmate.service.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            DaoAuthenticationProvider authenticationProvider,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN)))
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                    .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                    .requestMatchers("/api/auth/**", "/api/v1/health").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/categories").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/reviews").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/providers/profile").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.GET, "/api/providers/**")
                            .hasAnyRole("USER", "PROVIDER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/providers/**").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.PUT, "/api/providers/**")
                            .hasAnyRole("PROVIDER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/requests").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/requests/relevant").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.GET, "/api/requests/provider/requests/**").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.GET, "/api/requests").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/requests/*/matches")
                            .hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/requests/*/status")
                            .hasAnyRole("USER", "PROVIDER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/requests/*").hasRole("USER")
                        .requestMatchers(HttpMethod.DELETE, "/api/requests/*").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/requests/**")
                            .hasAnyRole("USER", "PROVIDER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/requests/**").hasRole("USER")
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/provider/**").hasAnyRole("PROVIDER", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

            @Bean
            CorsConfigurationSource corsConfigurationSource(
                @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins}") String allowedOrigins
            ) {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "Idempotency-Key"));
            configuration.setExposedHeaders(List.of("Content-Disposition"));
            configuration.setAllowCredentials(false);
            configuration.setMaxAge(3600L);

            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/api/**", configuration);
            return source;
            }

    @Bean
    DaoAuthenticationProvider authenticationProvider(
            UserDetailsServiceImpl userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
