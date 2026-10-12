package no.entur.anzu.index

import java.time.Instant

/** One status message, stored as it arrived. [ValidationRuns] folds these into runs. */
data class ValidationEvent(
    val validationReportId: String,
    val correlationId: String,
    val codespace: String,
    /** Blob path folder component only. Never used for authorization. */
    val referential: String,
    val stage: Stage,
    val status: RunStatus,
    /** Pub/Sub publish time, stable across redelivery. */
    val publishedAt: Instant,
)
