package no.entur.anzu.api.model

/** Ordered most severe first. UNKNOWN is last: it is the absence of a severity, not a claim. */
enum class Severity { CRITICAL, ERROR, WARNING, INFO, UNKNOWN }

data class ValidationEntry(
    val code: String,
    val severity: Severity,
    val message: String,
    val fileName: String?,
    val objectId: String?,
    val lineNumber: Int?,
    val columnNumber: Int?,
)
