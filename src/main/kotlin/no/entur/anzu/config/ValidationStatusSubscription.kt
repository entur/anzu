package no.entur.anzu.config

import com.google.api.core.ApiService
import com.google.cloud.pubsub.v1.Subscriber
import com.google.cloud.spring.pubsub.core.PubSubTemplate
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage
import no.entur.anzu.ingest.ValidationStatusListener
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The validation status subscriber, with its lifetime tied to the application context.
 *
 * A bare [Subscriber] bean is never shut down. Spring infers `close()` or `shutdown()` as a destroy
 * method and the subscriber offers neither — its stop API is `stopAsync()`. Left running it keeps
 * pulling while the context tears down, so whatever is in flight at SIGTERM meets a closing
 * datasource, nacks, and is redelivered. Harmless for the index, which drops duplicates, but it
 * burns delivery attempts.
 *
 * A [SmartLifecycle] rather than a `@PreDestroy`: Spring stops every lifecycle bean before it
 * destroys any bean, so the subscriber is reliably quiet before the datasource closes.
 */
class ValidationStatusSubscription(
    private val pubSubTemplate: PubSubTemplate,
    private val listener: ValidationStatusListener,
    private val subscription: String,
    private val shutdownTimeout: Duration,
) : SmartLifecycle {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var subscriber: Subscriber? = null

    @Volatile
    private var closing = false

    private val failureReported = AtomicBoolean(false)

    override fun start() {
        logger.info("Subscribing to the validation status queue on '{}'", subscription)
        closing = false
        failureReported.set(false)

        val started = pubSubTemplate.subscribe(subscription) { message: BasicAcknowledgeablePubsubMessage ->
            listener.receive(message)
        }
        watchForTerminalFailure(started)
        subscriber = started
    }

    /**
     * A status the client does not retry leaves the subscriber FAILED for good, and nothing else
     * notices: the pod stays healthy and keeps serving the API with ingest silently switched off.
     * At more than one replica the surviving pod takes the whole queue, so there is no backlog to
     * give it away either — this log line is the only evidence.
     */
    private fun watchForTerminalFailure(subscriber: Subscriber) {
        subscriber.addListener(
            object : ApiService.Listener() {
                override fun failed(from: ApiService.State, failure: Throwable) =
                    reportTerminalFailure(failure)
            },
            Executor { it.run() },
        )

        if (subscriber.state() == ApiService.State.FAILED) {
            reportTerminalFailure(subscriber.failureCause())
        }
    }

    private fun reportTerminalFailure(failure: Throwable) {
        if (closing || !failureReported.compareAndSet(false, true)) {
            return
        }
        logger.error(
            "The validation status subscriber failed permanently. Nothing more will be consumed " +
                "from '{}' until this pod is replaced. Check that the subscription exists and " +
                "that this service account holds roles/pubsub.subscriber on it.",
            subscription,
            failure,
        )
    }

    override fun stop() {
        closing = true
        val running = subscriber ?: return
        subscriber = null

        logger.info("Stopping the validation status subscriber on '{}'", subscription)
        running.stopAsync()
        try {
            running.awaitTerminated(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)
            logger.info("Stopped the validation status subscriber on '{}'", subscription)
        } catch (e: TimeoutException) {
            // Shutdown continues regardless. Anything still outstanding is redelivered, which is
            // the same answer the subscription would have reached on its own.
            logger.warn(
                "The validation status subscriber did not stop within {}. Messages still in flight will be redelivered.",
                shutdownTimeout,
                e,
            )
        } catch (e: IllegalStateException) {
            logger.warn("The validation status subscriber was not running cleanly at shutdown.", e)
        }
    }

    override fun isRunning(): Boolean = subscriber != null

    /**
     * The default phase, which is the highest, and beans are stopped highest first. Ingest
     * therefore goes quiet while the web layer is still draining and the datasource is still open,
     * rather than the other way round.
     */
    override fun getPhase(): Int = SmartLifecycle.DEFAULT_PHASE
}
