package com.trading.engine.controller;

import com.trading.engine.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class TestNotificationController {

    private final NotificationService notificationService;

    @GetMapping("/ping")
    public String send() {
        notificationService.sendMessage("Spring Boot → Telegram works.");
        return "Sent";
    }
}
