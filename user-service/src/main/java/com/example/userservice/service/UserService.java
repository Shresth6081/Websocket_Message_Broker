package com.example.userservice.service;

import com.example.userservice.dto.AuthResponse;
import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.RegisterRequest;
import com.example.userservice.model.User;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String cleanUsername = request.getUsername().trim();
        String cleanEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByUsernameIgnoreCase(cleanUsername)) {
            throw new IllegalArgumentException("Username already taken: " + cleanUsername);
        }
        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new IllegalArgumentException("Email already registered: " + cleanEmail);
        }

        String displayName = request.getDisplayName() != null && !request.getDisplayName().isBlank()
                ? request.getDisplayName().trim() : cleanUsername;

        User user = User.builder()
                .username(cleanUsername)
                .email(cleanEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .displayName(displayName)
                .build();

        userRepository.save(user);
        log.info("Registered new user: {}", user.getUsername());

        String token = jwtService.generateToken(user.getUsername(), user.getDisplayName());
        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .expiresIn(jwtService.getExpiration())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        String identifier = request.getUsername() != null ? request.getUsername().trim() : "";
        String password = request.getPassword() != null ? request.getPassword() : "";

        User user = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtService.generateToken(user.getUsername(), user.getDisplayName());
        log.info("User logged in: {}", user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .expiresIn(jwtService.getExpiration())
                .build();
    }

    public boolean existsByUsername(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        return userRepository.existsByUsernameIgnoreCase(username.trim());
    }
}
