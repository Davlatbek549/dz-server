package com.example.dz.server.chat.controller

import com.example.dz.server.chat.dto.ChatsResponse
import com.example.dz.server.chat.service.ChatService
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/chats")
class ChatController(private val chatService: ChatService) {

    @GetMapping
    fun listChats(@AuthenticationPrincipal userId: UUID): ChatsResponse =
        chatService.listChats(userId)
}
