package com.compartix.backend.service;

import com.compartix.backend.dto.request.LoginRequest;
import com.compartix.backend.dto.request.RegisterRequest;
import com.compartix.backend.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse loginConGoogle(String idToken);
    AuthResponse refreshToken(String refreshToken);
    void logout(String refreshToken);
}