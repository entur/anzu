package no.entur.anzu.api.model

enum class Severity { CRITICAL, ERROR, WARNING, INFO }

data class ValidationEntry(
    val code: String,
    val severity: Severity,
    val message: String,
    val fileName: String?,
    val objectId: String?,
    val lineNumber: Int?,
    val columnNumber: Int?,
)
