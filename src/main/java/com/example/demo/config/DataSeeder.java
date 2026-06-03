package com.example.demo.config;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    public DataSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.findByUsername("alice").isEmpty()) {
            userRepository.save(new User("alice", "alice@example.com"));
        }
        if (userRepository.findByUsername("bob").isEmpty()) {
            userRepository.save(new User("bob", "bob@example.com"));
        }
    }
}
