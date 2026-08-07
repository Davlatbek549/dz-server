package com.example.dz.server.common.web

import java.time.Instant
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class PingController {

    @GetMapping("/ping")
    fun ping() = PingResponse(status = "ok", time = Instant.now())

    data class PingResponse(
        val status: String,
        val time: Instant,
    )
}
