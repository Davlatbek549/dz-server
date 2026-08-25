package com.example.dz.server.auth.mail

import java.util.concurrent.Executor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
class MailAsyncConfig {

    /**
     * Small and bounded on purpose. Spring's default `SimpleAsyncTaskExecutor`
     * starts a thread per call and never reuses one, which on a burst of sign-ups
     * would spawn threads without limit on a container sized in fractions of a CPU.
     *
     * A full queue runs the send on the calling thread instead of dropping it:
     * the request pays for it, which is worse than not waiting but far better
     * than a reader never receiving their code.
     */
    @Bean(MailDispatcher.MAIL_EXECUTOR)
    fun mailExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 1
        maxPoolSize = 3
        setQueueCapacity(50)
        setThreadNamePrefix("mail-")
        setRejectedExecutionHandler(java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy())
        initialize()
    }
}
