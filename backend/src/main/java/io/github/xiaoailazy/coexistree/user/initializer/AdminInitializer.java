package io.github.xiaoailazy.coexistree.user.initializer;

import io.github.xiaoailazy.coexistree.user.config.AdminProperties;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import io.github.xiaoailazy.coexistree.user.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder, AdminProperties adminProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminProperties.getPassword() == null || adminProperties.getPassword().isBlank()) {
            throw new IllegalStateException("Admin password must be configured via ADMIN_INITIAL_PASSWORD environment variable");
        }

        if (!userRepository.existsByUsername(adminProperties.getUsername())) {
            UserEntity admin = new UserEntity();
            admin.setUsername(adminProperties.getUsername());
            admin.setPasswordHash(passwordEncoder.encode(adminProperties.getPassword()));
            admin.setDisplayName(adminProperties.getDisplayName());
            admin.setRole(UserRole.SUPER_ADMIN);
            userRepository.save(admin);
        }
    }
}
