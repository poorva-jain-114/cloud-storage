package com.example.demo.config;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        userRepository.findByUsername("alice").ifPresentOrElse(
            alice -> {
                if (alice.getPassword() == null) {
                    alice.setPassword(passwordEncoder.encode("password"));
                    userRepository.save(alice);
                }
            },
            () -> {
                User alice = new User("alice", "alice@example.com");
                alice.setPassword(passwordEncoder.encode("password"));
                userRepository.save(alice);
            }
        );

        userRepository.findByUsername("bob").ifPresentOrElse(
            bob -> {
                if (bob.getPassword() == null) {
                    bob.setPassword(passwordEncoder.encode("password"));
                    userRepository.save(bob);
                }
            },
            () -> {
                User bob = new User("bob", "bob@example.com");
                bob.setPassword(passwordEncoder.encode("password"));
                userRepository.save(bob);
            }
        );
    }
}
