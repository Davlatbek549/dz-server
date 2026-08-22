package com.example.dz.server.auth.repository

import com.example.dz.server.auth.entity.AuthProvider
import com.example.dz.server.auth.entity.LinkedAccount
import java.util.Optional
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface LinkedAccountRepository : JpaRepository<LinkedAccount, UUID> {

    fun findByProviderAndSubject(provider: AuthProvider, subject: String): Optional<LinkedAccount>
}
