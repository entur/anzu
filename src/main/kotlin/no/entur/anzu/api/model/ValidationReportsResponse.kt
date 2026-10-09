package no.entur.anzu.api.model

import java.time.OffsetDateTime

enum class RunStatusResponse { IN_PROGRESS, OK, FAILED }

/** `GET /validation-reports/{correlationId}` */
data class ValidationReportsResponse(
    val correlationId: String,
    val codespace: String,
    val validationRuns: List<ValidationRunSummary>,
)

/**
 * Collection element. Carries no codespace — it is hoisted to the envelope so it appears once.
 *
 * [entries] null means no report is available, an empty list means the run found nothing.
 * The field is present either way.
 */
data class ValidationRunSummary(
    val stage: String,
    val status: RunStatusResponse,
    val startedAt: OffsetDateTime?,
    val completedAt: OffsetDateTime?,
    val entries: List<ValidationEntry>?,
)

/**
 * `GET /validation-reports/{correlationId}/{stage}`
 */
data class ValidationRunDetail(
    val correlationId: String,
    val codespace: String,
    val stage: String,
    val status: RunStatusResponse,
    val startedAt: OffsetDateTime?,
    val completedAt: OffsetDateTime?,
    val entries: List<ValidationEntry>?,
)
