package com.example.dz.server.auth.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.mock.env.MockEnvironment

/**
 * The audience set is the only thing standing between this server and every Google ID token in
 * the world — they are all signed by the same keys. These pin how it is read from configuration,
 * because a mis-parsed list fails open in the worst possible way: an empty set, or a widened one.
 */
class GoogleOAuthPropertiesTest {

    private fun bind(value: String?): GoogleOAuthProperties {
        val environment = MockEnvironment()
        if (value != null) environment.setProperty("dz.oauth.google.client-ids", value)
        val sources = ConfigurationPropertySources.from(environment.propertySources)
        return Binder(sources)
            .bind("dz.oauth.google", GoogleOAuthProperties::class.java)
            .orElseGet { GoogleOAuthProperties() }
    }

    @Test
    fun `a comma-separated pair binds to both audiences`() {
        val properties = bind("web-client.apps.googleusercontent.com,ios-client.apps.googleusercontent.com")

        assertEquals(
            setOf("web-client.apps.googleusercontent.com", "ios-client.apps.googleusercontent.com"),
            properties.acceptedAudiences,
            "Android and iOS mint tokens against different clients; both have to be accepted",
        )
        assertTrue(properties.isEnabled)
    }

    @Test
    fun `surrounding whitespace does not create an audience that never matches`() {
        val properties = bind(" web-client.apps.googleusercontent.com , ios-client.apps.googleusercontent.com ")

        assertEquals(
            setOf("web-client.apps.googleusercontent.com", "ios-client.apps.googleusercontent.com"),
            properties.acceptedAudiences,
        )
    }

    @Test
    fun `a trailing comma cannot widen the set`() {
        val properties = bind("web-client.apps.googleusercontent.com,")

        // An empty entry that reached the set would match a token carrying no audience at all.
        assertEquals(setOf("web-client.apps.googleusercontent.com"), properties.acceptedAudiences)
        assertFalse(properties.acceptedAudiences.contains(""))
    }

    @Test
    fun `an unset value leaves sign-in switched off`() {
        val properties = bind(null)

        assertTrue(properties.acceptedAudiences.isEmpty())
        assertFalse(
            properties.isEnabled,
            "with no audience to check against, the endpoint must refuse rather than accept",
        )
    }
}
