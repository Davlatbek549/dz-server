package com.example.dz.server.common.storage

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Picks the image storage from configuration, and for [StorageType.LOCAL] serves its folder at
 * `/files/` (public, like any profile photo on the web; see SecurityConfig).
 */
@Configuration
class StorageConfig(private val properties: StorageProperties) : WebMvcConfigurer {

    private val localDirectory: Path by lazy {
        Path.of(properties.localDirectory).toAbsolutePath().normalize().also { Files.createDirectories(it) }
    }

    @Bean
    fun imageStorage(): ImageStorage = when (properties.type) {
        StorageType.LOCAL -> LocalImageStorage(localDirectory, properties.publicBaseUrl.trimEnd('/'))
        StorageType.NONE -> UnavailableImageStorage
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        if (properties.type != StorageType.LOCAL) return
        registry.addResourceHandler("/files/**")
            .addResourceLocations("file:$localDirectory/")
            // Every upload gets a new name, so a stored file never changes and can be cached for good.
            .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
    }
}
