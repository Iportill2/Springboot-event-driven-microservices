package com.ecommerce.user.config;

import com.ecommerce.user.domain.AppUser;
import com.ecommerce.user.domain.Role;
import com.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                      @Value("${app.seed.admin-password:admin123}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(new AppUser("admin", "admin@ecommerce.local", passwordEncoder.encode(adminPassword), Role.ADMIN));
            log.info("Admin user created (username=admin)");
        }
    }
}