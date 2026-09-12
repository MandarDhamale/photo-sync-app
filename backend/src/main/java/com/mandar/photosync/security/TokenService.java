package com.mandar.photosync.security;

import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class TokenService {

    private static final String TOKEN_FILE = "pairing_token.txt";
    private String token;

    @PostConstruct
    public void init() throws IOException {
        Path path = Paths.get(TOKEN_FILE);
        if (Files.exists(path)) {
            token = Files.readString(path).trim();
        } else {
            token = UUID.randomUUID().toString();
            Files.writeString(path, token);
        }
    }

    public String getToken() {
        return token;
    }
}

