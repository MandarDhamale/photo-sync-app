package com.mandar.photosync.controller;

import com.mandar.photosync.security.NetworkUtils;
import com.mandar.photosync.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class PairingController {

    private final TokenService tokenService;

    @Value("${server.port:8080}")
    private int serverPort;

    public PairingController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/api/pairing")
    public Map<String, Object> getPairingInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("ip", NetworkUtils.getLocalIpAddress());
        info.put("port", serverPort);
        info.put("token", tokenService.getToken());
        return info;
    }
}

