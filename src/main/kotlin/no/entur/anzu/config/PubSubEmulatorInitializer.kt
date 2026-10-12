package no.entur.anzu.config

import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.core.NoCredentialsProvider
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider
import com.google.api.gax.rpc.NotFoundException
import com.google.cloud.pubsub.v1.SubscriptionAdminClient
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings
import com.google.cloud.pubsub.v1.TopicAdminClient
import com.google.cloud.pubsub.v1.TopicAdminSettings
import com.google.pubsub.v1.PushConfig
import com.google.pubsub.v1.SubscriptionName
import com.google.pubsub.v1.TopicName
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Creates the validation status topic and anzu's subscription in a Pub/Sub emulator.
 *
 * The emulator starts empty, and antu is not running to create the topic, so without this the
 * subscriber has nothing to attach to.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.cloud.gcp.pubsub.emulator-host")
class PubSubEmulatorInitializer(
    @Value("\${spring.cloud.gcp.pubsub.emulator-host}") private val emulatorHost: String,
    @Value("\${spring.cloud.gcp.project-id}") private val projectId: String,
    @Value("\${anzu.pubsub.validation-status-topic}") private val topic: String,
    @Value("\${anzu.pubsub.validation-status-subscription}") private val subscription: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Resolve no credentials at all against an emulator. Without this spring-cloud-gcp falls back
     * to the developer's gcloud application default credentials, which local development has no
     * business reading.
     */
    @Bean
    fun emulatorCredentialsProvider(): CredentialsProvider = NoCredentialsProvider.create()

    @PostConstruct
    fun createTopicAndSubscription() {
        logger.info("Using the Pub/Sub emulator at '{}'. No real project is contacted.", emulatorHost)

        val channel = InstantiatingGrpcChannelProvider.newBuilder()
            .setChannelConfigurator { it.usePlaintext() }
            .setEndpoint(emulatorHost)
            .build()

        val topicName = TopicName.of(projectId, topic)
        val subscriptionName = SubscriptionName.of(projectId, subscription)

        TopicAdminClient.create(
            TopicAdminSettings.newBuilder()
                .setTransportChannelProvider(channel)
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build()
        ).use { client ->
            try {
                client.getTopic(topicName)
            } catch (_: NotFoundException) {
                logger.info("Creating topic '{}'", topic)
                client.createTopic(topicName)
            }
        }

        SubscriptionAdminClient.create(
            SubscriptionAdminSettings.newBuilder()
                .setTransportChannelProvider(channel)
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build()
        ).use { client ->
            try {
                client.getSubscription(subscriptionName)
            } catch (_: NotFoundException) {
                logger.info("Creating subscription '{}'", subscription)
                client.createSubscription(
                    subscriptionName,
                    topicName,
                    PushConfig.getDefaultInstance(),
                    60,
                )
            }
        }
    }
}
