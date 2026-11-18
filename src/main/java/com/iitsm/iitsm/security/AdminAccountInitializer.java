package com.iitsm.iitsm.security;

import com.iitsm.iitsm.persistence.entity.AppUserEntity;
import com.iitsm.iitsm.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountInitializer implements ApplicationRunner {
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminAccountInitializer(AppUserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.security.admin.username:admin}") String username,
                                   @Value("${app.security.admin.password:ChangeMe123!}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(new AppUserEntity(username, passwordEncoder.encode(password), "ADMIN"));
        }
    }
}
