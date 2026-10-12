package no.entur.anzu.ingest

import no.entur.anzu.index.RunStatus
import no.entur.anzu.index.Stage
import no.entur.anzu.index.ValidationEvent
import java.time.Instant

/**
 * One status event from `AntuNetexValidationStatusQueue`, parsed.
 *
 * [publishedAt] is the Pub/Sub publish time, not the time of ingest. It is stable across
 * redelivery, which is what lets the index drop duplicates.
 */
data class StatusMessage(
    val validationReportId: String,
    val correlationId: String,
    val codespace: String,
    val referential: String,
    val stage: Stage,
    val status: RunStatus,
    val publishedAt: Instant,
) {
    fun toEvent() = ValidationEvent(
        validationReportId = validationReportId,
        correlationId = correlationId,
        codespace = codespace,
        referential = referential,
        stage = stage,
        status = status,
        publishedAt = publishedAt,
    )
}

sealed interface ParseResult {
    data class Parsed(val message: StatusMessage) : ParseResult

    /**
     * The message will be exactly as unprocessable next time, so it is acked and logged rather
     * than nacked.
     */
    data class Unprocessable(val reason: String) : ParseResult
}
