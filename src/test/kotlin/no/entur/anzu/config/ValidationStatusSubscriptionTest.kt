package no.entur.anzu.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.google.api.core.ApiService
import com.google.cloud.pubsub.v1.Subscriber
import com.google.cloud.spring.pubsub.core.PubSubTemplate
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage
import no.entur.anzu.ingest.ValidationStatusListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * The subscriber's lifetime is the context's.
 *
 * A subscriber left running while the context tears down keeps pulling against a closing
 * datasource, so every message in flight at SIGTERM fails and is redelivered — on every pod
 * replacement, which at more than one replica is twice per deploy.
 */
class ValidationStatusSubscriptionTest {

    private val subscriber: Subscriber = mock()

    private val pubSubTemplate: PubSubTemplate = mock {
        on { subscribe(any(), any<Consumer<BasicAcknowledgeablePubsubMessage>>()) } doReturn subscriber
    }

    private val logged = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun captureLogging() {
        logged.start()
        (LoggerFactory.getLogger(ValidationStatusSubscription::class.java) as Logger)
            .addAppender(logged)
    }

    @AfterEach
    fun releaseLogging() {
        (LoggerFactory.getLogger(ValidationStatusSubscription::class.java) as Logger)
            .detachAppender(logged)
    }

    @Test
    fun `closing the context stops the subscriber`() {
        context().use { it.refresh() }

        verify(subscriber).stopAsync()
    }

    /**
     * The datasource is destroyed, not stopped, and destruction order between beans with no
     * dependency between them is unspecified. Stopping as a lifecycle rather than in a
     * `@PreDestroy` is what puts the subscriber reliably on the near side of that.
     */
    @Test
    fun `the subscriber stops before any bean is destroyed`() {
        val order = mutableListOf<String>()
        whenever(subscriber.stopAsync()).thenAnswer {
            order += "subscriber stopped"
            subscriber
        }

        context().use { context ->
            context.registerBean(DestructionRecorder::class.java, Supplier { DestructionRecorder(order) })
            context.refresh()
            // Touch it, so a lazily created bean is actually instantiated and has a destroy callback.
            context.getBean(DestructionRecorder::class.java)
        }

        assertEquals(listOf("subscriber stopped", "bean destroyed"), order)
    }

    /**
     * A subscriber that will not come to rest must not take the shutdown down with it. Whatever is
     * still outstanding is redelivered, which is the same answer the subscription reaches on its
     * own once the ack deadline passes.
     */
    @Test
    fun `a subscriber that will not stop in time does not fail the shutdown`() {
        whenever(subscriber.awaitTerminated(any(), any())).thenThrow(TimeoutException())

        val subscription = subscription()
        subscription.start()

        assertDoesNotThrow { subscription.stop() }
        assertFalse(subscription.isRunning(), "a subscriber that timed out is still stopped")
    }

    /** A pod is often replaced precisely because the subscriber failed, so this is the live path. */
    @Test
    fun `a subscriber that already failed is not waited for`() {
        whenever(subscriber.awaitTerminated(any(), any()))
            .thenThrow(IllegalStateException("expected the service to be TERMINATED, but was FAILED"))

        val subscription = subscription()
        subscription.start()

        assertDoesNotThrow { subscription.stop() }
        assertFalse(subscription.isRunning(), "a subscriber that failed is still stopped")
    }

    /**
     * At one replica a dead subscriber stops ingest outright and is obvious. At two the other pod
     * absorbs the whole queue and everything still looks healthy, so the only evidence is this log
     * line.
     */
    @Test
    fun `a subscriber that fails permanently is reported`() {
        subscription().start()

        subscriberListener().failed(ApiService.State.RUNNING, RuntimeException("PERMISSION_DENIED"))

        val error = errors().single()
        assertTrue(
            error.formattedMessage.contains("a-subscription"),
            "the subscription is what someone has to go and look at: ${error.formattedMessage}",
        )
    }

    /**
     * Guava replays nothing to a listener added after the fact, and the failures that matter most —
     * a subscription that does not exist, a service account without rights to it — are exactly the
     * ones that land while the subscriber is still starting.
     */
    @Test
    fun `a subscriber that failed before the listener was attached is still reported`() {
        whenever(subscriber.state()) doReturn ApiService.State.FAILED
        whenever(subscriber.failureCause()) doReturn RuntimeException("NOT_FOUND")

        subscription().start()

        assertEquals(1, errors().size)
    }

    /** Both the listener and the state check can see the same failure. Only one of them says so. */
    @Test
    fun `a failure is reported once`() {
        subscription().start()
        val listener = subscriberListener()
        val cause = RuntimeException("PERMISSION_DENIED")

        listener.failed(ApiService.State.RUNNING, cause)
        listener.failed(ApiService.State.RUNNING, cause)

        assertEquals(1, errors().size)
    }

    /** Stopping is not failing. A deploy must not look like an outage. */
    @Test
    fun `a failure while shutting down is not reported`() {
        val subscription = subscription()
        subscription.start()
        val listener = subscriberListener()
        subscription.stop()

        listener.failed(ApiService.State.STOPPING, RuntimeException("cancelled"))

        assertTrue(errors().isEmpty(), "shutdown reported a failure: ${errors().map { it.formattedMessage }}")
    }

    private fun subscriberListener(): ApiService.Listener {
        val listener = argumentCaptor<ApiService.Listener>()
        verify(subscriber).addListener(listener.capture(), any())
        return listener.firstValue
    }

    private fun errors() = logged.list.filter { it.level == Level.ERROR }

    private fun subscription() = ValidationStatusSubscription(
        pubSubTemplate,
        mock(),
        "a-subscription",
        Duration.ofMillis(1),
    )

    private fun context() = AnnotationConfigApplicationContext().apply {
        // Spring Boot installs this for the real application; a bare context converts no durations.
        beanFactory.conversionService = ApplicationConversionService.getSharedInstance()
        beanFactory.registerSingleton("pubSubTemplate", pubSubTemplate)
        beanFactory.registerSingleton("validationStatusListener", mock<ValidationStatusListener>())
        environment.propertySources.addFirst(
            MapPropertySource(
                "test",
                mapOf("anzu.pubsub.validation-status-subscription" to "a-subscription"),
            )
        )
        register(PubSubConfig::class.java)
    }

    class DestructionRecorder(private val order: MutableList<String>) : DisposableBean {
        override fun destroy() {
            order += "bean destroyed"
        }
    }
}
