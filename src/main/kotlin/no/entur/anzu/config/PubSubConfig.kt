package no.entur.anzu.config

import com.google.cloud.pubsub.v1.Subscriber
import com.google.cloud.spring.pubsub.core.PubSubTemplate
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage
import no.entur.anzu.ingest.ValidationStatusListener
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun validationStatusSubscriber(
        pubSubTemplate: PubSubTemplate,
        listener: ValidationStatusListener,
        @Value("\${anzu.pubsub.validation-status-subscription}") subscription: String,
    ): Subscriber {
        logger.info("Subscribing to the validation status queue on '{}'", subscription)
        return pubSubTemplate.subscribe(subscription) { message: BasicAcknowledgeablePubsubMessage ->
            listener.receive(message)
        }
    }
}
