package no.entur.anzu.ingest

import no.entur.anzu.index.RunStatus
import no.entur.anzu.index.Stage
import java.time.Instant

/**
 * One status event from `AntuNetexValidationStatusQueue`, parsed.
 *
 * [startedAt] and [completedAt] are the Pub/Sub publish time, not the time of ingest. Publish time
 * is stable across redelivery, which is what keeps the upsert idempotent.
 */
data class StatusMessage(
    val validationReportId: String,
    val correlationId: String,
    val codespace: String,
    val referential: String,
    val stage: Stage,
    val status: RunStatus,
    val startedAt: Instant?,
    val completedAt: Instant?,
)

sealed interface ParseResult {
    data class Parsed(val message: StatusMessage) : ParseResult

    /**
     * The message will be exactly as unprocessable next time, so it is acked and logged rather
     * than nacked.
     */
    data class Unprocessable(val reason: String) : ParseResult
}
