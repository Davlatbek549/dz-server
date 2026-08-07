package com.example.dz.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class DzServerApplication

fun main(args: Array<String>) {
    runApplication<DzServerApplication>(*args)
}
