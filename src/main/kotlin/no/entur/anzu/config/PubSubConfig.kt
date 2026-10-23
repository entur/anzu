package no.entur.anzu.config

import com.google.cloud.spring.pubsub.core.PubSubTemplate
import no.entur.anzu.ingest.ValidationStatusListener
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Subscribes to antu's validation status queue.
 *
 * The subscription is anzu's **own**, never marduk's — sharing marduk's would have the two stealing
 * each other's messages.
 *
 * Disabled in tests via `anzu.pubsub.enabled=false`, so no test ever opens a subscriber.
 */
@Configuration
@ConditionalOnProperty(value = ["anzu.pubsub.enabled"], havingValue = "true", matchIfMissing = true)
class PubSubConfig {

    /**
     * [shutdownTimeout] must stay below `spring.lifecycle.timeout-per-shutdown-phase`, or Spring
     * gives up on the phase before the subscriber has finished stopping and says so in the log.
     */
    @Bean
    fun validationStatusSubscription(
        pubSubTemplate: PubSubTemplate,
        listener: ValidationStatusListener,
        @Value("\${anzu.pubsub.validation-status-subscription}") subscription: String,
        @Value("\${anzu.pubsub.shutdown-timeout:5s}") shutdownTimeout: Duration,
    ): ValidationStatusSubscription =
        ValidationStatusSubscription(pubSubTemplate, listener, subscription, shutdownTimeout)
}
