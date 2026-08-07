package com.example.dz.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DzServerApplication

fun main(args: Array<String>) {
    runApplication<DzServerApplication>(*args)
}
