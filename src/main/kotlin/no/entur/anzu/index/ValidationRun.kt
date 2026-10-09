package no.entur.anzu.index

import java.time.Instant

/** One index row. Report bodies are never stored — only that a run happened and where its report lives. */
data class ValidationRun(
    val validationReportId: String,
    val correlationId: String,
    val codespace: String,
    /** Blob path folder component only. Never used for authorization. */
    val referential: String,
    val stage: Stage,
    val status: RunStatus,
    val startedAt: Instant?,
    val completedAt: Instant?,
)
