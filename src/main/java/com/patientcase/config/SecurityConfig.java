package com.patientcase.config;

import com.patientcase.security.UserDetailsServiceImpl;
import com.patientcase.security.MustChangePasswordFilter;
import com.patientcase.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService,
                          ApplicationEventPublisher eventPublisher,
                          UserRepository userRepository) {
        this.userDetailsService = userDetailsService;
        this.eventPublisher = eventPublisher;
        this.userRepository = userRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/fonts/**", "/error").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/kiosk", "/kiosk/language", "/kiosk/login", "/kiosk/register").permitAll()
                .requestMatchers("/kiosk/**").hasRole("PATIENT")
                .requestMatchers("/api/kiosk/**").hasRole("PATIENT")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/intakes/**").hasAnyRole("ADMIN", "DOCTOR", "NURSE")
                .requestMatchers("/patients/**").hasAnyRole("ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST")
                .requestMatchers("/cases/**").hasAnyRole("ADMIN", "DOCTOR", "NURSE")
                .requestMatchers("/encounters/**").hasAnyRole("ADMIN", "DOCTOR", "NURSE")
                .requestMatchers("/appointments/**").hasAnyRole("ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST")
                .requestMatchers("/documents/**").hasAnyRole("ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST")
                .requestMatchers("/dashboard").hasAnyRole("ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(authenticationSuccessHandler())
                .failureUrl("/login?error=true")
                .usernameParameter("username")
                .passwordParameter("password")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(logoutSuccessHandler())
                .addLogoutHandler(new SecurityContextLogoutHandler())
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.deny())
                .contentTypeOptions(contentType -> {})
                .referrerPolicy(referrer ->
                    referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            .csrf(csrf -> {})
            .addFilterAfter(mustChangePasswordFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public MustChangePasswordFilter mustChangePasswordFilter() {
        return new MustChangePasswordFilter(userRepository);
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            boolean isPatient = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_PATIENT".equals(a.getAuthority()));
            response.sendRedirect(isPatient ? "/kiosk/home" : "/dashboard");
        };
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            if (authentication != null) {
                eventPublisher.publishEvent(new LogoutSuccessEvent(authentication));
            }
            response.sendRedirect("/login?logout=true");
        };
    }
}
