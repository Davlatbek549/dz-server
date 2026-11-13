package com.example.dz.server.friends.controller

import com.example.dz.server.friends.dto.FriendRequestResponse
import com.example.dz.server.friends.dto.FriendRequestsResponse
import com.example.dz.server.friends.dto.FriendResponse
import com.example.dz.server.friends.dto.FriendsResponse
import com.example.dz.server.friends.dto.SendFriendRequestRequest
import com.example.dz.server.friends.dto.SuggestedUsersResponse
import com.example.dz.server.friends.service.FriendService
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

/**
 * `/requests` is a literal segment, so Spring ranks it above `/{friendId}`
 * regardless of declaration order -- a request for the inbox is never
 * swallowed by the single-friend lookup.
 */
@RestController
@RequestMapping("/api/v1/friends")
class FriendController(private val friendService: FriendService) {

    @GetMapping
    fun listFriends(@AuthenticationPrincipal userId: UUID): FriendsResponse =
        friendService.listFriends(userId)

    @GetMapping("/requests")
    fun listPendingRequests(@AuthenticationPrincipal userId: UUID): FriendRequestsResponse =
        friendService.listPendingRequests(userId)

    @GetMapping("/suggested")
    fun listSuggested(@AuthenticationPrincipal userId: UUID): SuggestedUsersResponse =
        friendService.listSuggested(userId)

    @GetMapping("/{friendId}")
    fun getFriend(@AuthenticationPrincipal userId: UUID, @PathVariable friendId: UUID): FriendResponse =
        friendService.getFriend(userId, friendId)

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendRequest(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: SendFriendRequestRequest,
    ): FriendRequestResponse = friendService.sendRequest(userId, request.targetUserId)

    @PostMapping("/requests/{requestId}/accept")
    fun acceptRequest(@AuthenticationPrincipal userId: UUID, @PathVariable requestId: UUID): FriendResponse =
        friendService.acceptRequest(userId, requestId)

    @PostMapping("/requests/{requestId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun declineRequest(@AuthenticationPrincipal userId: UUID, @PathVariable requestId: UUID) =
        friendService.declineRequest(userId, requestId)
}
