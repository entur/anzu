package no.entur.anzu.ingest

/** Attribute keys on the validation status queue. */
object PubSubAttributes {
    const val CORRELATION_ID = "EnturValidationCorrelationId"
    const val DATASET_CODESPACE = "EnturDatasetCodespace"
    const val DATASET_REFERENTIAL = "EnturDatasetReferential"
    const val VALIDATION_REPORT_ID = "EnturValidationReportId"
    const val VALIDATION_STAGE = "EnturValidationStage"

    /** Not read here; the subscription filters on it. */
    const val VALIDATION_CLIENT = "EnturValidationClient"
}
