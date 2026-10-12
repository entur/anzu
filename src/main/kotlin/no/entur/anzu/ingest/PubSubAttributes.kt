package no.entur.anzu.ingest

/** Attribute keys on the validation status queue. */
object PubSubAttributes {
    const val CORRELATION_ID = "EnturValidationCorrelationId"
    const val DATASET_CODESPACE = "EnturDatasetCodespace"
    const val DATASET_REFERENTIAL = "EnturDatasetReferential"
    const val VALIDATION_REPORT_ID = "EnturValidationReportId"
    const val VALIDATION_STAGE = "EnturValidationStage"
    const val VALIDATION_CLIENT = "EnturValidationClient"

    const val DELIVERY_ATTEMPT = "googclient_deliveryattempt"

    /**
     * 0 when the subscription has no dead letter policy, or the value is not a number — Pub/Sub
     * sets this itself, but a malformed value must not take ingest down.
     */
    fun deliveryAttempt(attributes: Map<String, String>): Int =
        attributes[DELIVERY_ATTEMPT]?.toIntOrNull() ?: 0
}
