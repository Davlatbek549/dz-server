package com.example.dz.server.chat.controller

import com.example.dz.server.chat.dto.MessageResponse
import com.example.dz.server.chat.dto.MessagesResponse
import com.example.dz.server.chat.dto.SendMessageRequest
import com.example.dz.server.chat.service.ChatService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/messages")
class MessageController(private val chatService: ChatService) {

    @GetMapping("/{friendId}")
    fun getMessages(@AuthenticationPrincipal userId: UUID, @PathVariable friendId: UUID): MessagesResponse =
        chatService.getMessages(userId, friendId)

    @PostMapping("/{friendId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendMessage(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable friendId: UUID,
        @Valid @RequestBody request: SendMessageRequest,
    ): MessageResponse = chatService.sendMessage(userId, friendId, request.text)
}
