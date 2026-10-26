package no.entur.anzu.ingest

import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage
import no.entur.anzu.index.ValidationEventRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.TransientDataAccessException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.stereotype.Component
import java.time.Instant

enum class Disposition { ACK, NACK }

/**
 * Consumes antu's validation status events and maintains the index.
 *
 * Ingest never touches GCS. It records only that a run happened and where its report will live.
 */
@Component
class ValidationStatusListener(
    private val repository: ValidationEventRepository,
    private val parser: StatusMessageParser,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun receive(message: BasicAcknowledgeablePubsubMessage) {
        val pubsubMessage = message.pubsubMessage
        val publishedAt = Instant.ofEpochSecond(
            pubsubMessage.publishTime.seconds,
            pubsubMessage.publishTime.nanos.toLong(),
        )

        when (handle(pubsubMessage.data.toStringUtf8(), pubsubMessage.attributesMap, publishedAt)) {
            Disposition.ACK -> message.ack()
            Disposition.NACK -> message.nack()
        }
    }

    /**
     * Nack **only** when the next attempt could plausibly succeed.
     *
     * Redelivery is the right answer to a transient fault and the wrong answer to a deterministic
     * one, where it buys nothing and costs an endlessly redelivered message.
     */
    fun handle(body: String, attributes: Map<String, String>, publishedAt: Instant): Disposition =
        when (val result = parser.parse(body, attributes, publishedAt)) {
            is ParseResult.Unprocessable -> {
                // Acked on purpose. It will be exactly as unprocessable next time.
                logger.warn(
                    "Discarding a validation status message: {}. Report id '{}', correlation id '{}'",
                    result.reason,
                    attributes[PubSubAttributes.VALIDATION_REPORT_ID],
                    attributes[PubSubAttributes.CORRELATION_ID],
                )
                Disposition.ACK
            }

            is ParseResult.Parsed -> {
                logger.info(
                    "Storing validation status message in database. Stage {}, report id '{}', correlation id '{}'",
                    attributes[PubSubAttributes.VALIDATION_STAGE],
                    attributes[PubSubAttributes.VALIDATION_REPORT_ID],
                    attributes[PubSubAttributes.CORRELATION_ID],
                )
                val disposition = store(result.message)
                logger.info(
                    "Done storing validation status message in database. Stage {}, report id '{}', correlation id '{}'",
                    attributes[PubSubAttributes.VALIDATION_STAGE],
                    attributes[PubSubAttributes.VALIDATION_REPORT_ID],
                    attributes[PubSubAttributes.CORRELATION_ID],
                )
                return disposition
            }
        }

    private fun store(message: StatusMessage): Disposition =
        try {
            repository.append(message.toEvent())
            Disposition.ACK
        } catch (e: DataIntegrityViolationException) {
            // Not the append itself — duplicates are handled by `on conflict` and never throw. This
            // is a genuine constraint violation, and the same row will violate it again.
            logger.error(
                "Constraint violation storing the run for report id '{}'. Discarding.",
                message.validationReportId,
                e,
            )
            Disposition.ACK
        } catch (e: CannotGetJdbcConnectionException) {
            retryable(message, e)
        } catch (e: ConcurrencyFailureException) {
            // Lock timeouts, deadlock victims, optimistic failures.
            retryable(message, e)
        } catch (e: TransientDataAccessException) {
            retryable(message, e)
        } catch (e: RuntimeException) {
            // Unclassified, most likely transient, so retried rather than dropped. With no dead
            // letter topic, a deterministic failure here is redelivered until retention expires.
            retryable(message, e)
        }

    private fun retryable(message: StatusMessage, e: RuntimeException): Disposition {
        logger.warn(
            "Could not store the run for report id '{}'. Nacking for redelivery.",
            message.validationReportId,
            e,
        )
        return Disposition.NACK
    }
}
