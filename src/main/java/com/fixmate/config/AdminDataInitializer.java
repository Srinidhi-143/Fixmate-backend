package com.fixmate.config;

import com.fixmate.entity.User;
import com.fixmate.entity.UserRole;
import com.fixmate.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    public AdminDataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (adminEmail.isBlank() && adminPassword.isBlank()) {
            return;
        }
        if (adminEmail.isBlank() || adminPassword.length() < 8 || adminPassword.length() > 72) {
            throw new IllegalArgumentException("Set ADMIN_EMAIL and ADMIN_PASSWORD (8-72 characters) together");
        }
        String email = adminEmail.trim().toLowerCase();

        if (!userRepository.existsByEmail(email)) {
            userRepository.save(new User(
                    "FixMate Admin",
                    email,
                    passwordEncoder.encode(adminPassword),
                    UserRole.ADMIN
            ));

            System.out.println("FixMate admin account created: " + email);
        }
    }
}
