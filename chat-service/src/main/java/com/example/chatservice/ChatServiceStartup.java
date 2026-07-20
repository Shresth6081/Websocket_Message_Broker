package com.example.chatservice;

import com.example.chatservice.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatServiceStartup implements ApplicationRunner {

    private final ChatRoomService chatRoomService;

    @Override
    public void run(ApplicationArguments args) {
        chatRoomService.initializeDefaultRooms();
        log.info("Chat service started successfully.");
    }
}
