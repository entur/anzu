package no.entur.anzu.config

import org.entur.oauth2.AuthorizedWebClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/** Only needed when role assignments come from baba, which is the only outbound call anzu makes. */
@Configuration
@ConditionalOnProperty(value = ["anzu.security.role-assignment-extractor"], havingValue = "baba")
class OAuth2Config {

    /** Anzu's own machine-to-machine client. A caller's token is never forwarded. */
    @Bean
    fun webClient(
        properties: OAuth2ClientProperties,
        @Value("\${anzu.oauth2.client.audience}") audience: String,
    ): WebClient =
        AuthorizedWebClientBuilder(WebClient.builder())
            .withOAuth2ClientProperties(properties)
            .withAudience(audience)
            .withClientRegistrationId("anzu")
            .build()
}
