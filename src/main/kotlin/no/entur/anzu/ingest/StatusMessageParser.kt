package no.entur.anzu.ingest

import no.entur.anzu.index.RunStatus
import no.entur.anzu.index.Stage
import no.entur.anzu.ingest.PubSubAttributes.CORRELATION_ID
import no.entur.anzu.ingest.PubSubAttributes.DATASET_CODESPACE
import no.entur.anzu.ingest.PubSubAttributes.DATASET_REFERENTIAL
import no.entur.anzu.ingest.PubSubAttributes.VALIDATION_REPORT_ID
import no.entur.anzu.ingest.PubSubAttributes.VALIDATION_STAGE
import org.springframework.stereotype.Component
import java.time.Instant

/** Turns the attributes and body of a status message into a [StatusMessage]. */
@Component
class StatusMessageParser {

    fun parse(body: String, attributes: Map<String, String>, publishedAt: Instant): ParseResult {
        val reportId = attributes[VALIDATION_REPORT_ID]
            ?: return ParseResult.Unprocessable("no $VALIDATION_REPORT_ID")
        val correlationId = attributes[CORRELATION_ID]
            ?: return ParseResult.Unprocessable("no $CORRELATION_ID")
        val codespace = attributes[DATASET_CODESPACE]
            ?: return ParseResult.Unprocessable("no $DATASET_CODESPACE")
        val referential = attributes[DATASET_REFERENTIAL]
            ?: return ParseResult.Unprocessable("no $DATASET_REFERENTIAL")

        val stageConstant = attributes[VALIDATION_STAGE]
            ?: return ParseResult.Unprocessable("no $VALIDATION_STAGE")
        val stage = STAGES[stageConstant]
            ?: return ParseResult.Unprocessable("unrecognised stage '$stageConstant'")

        val status = STATUSES[body.trim()]
            ?: return ParseResult.Unprocessable("unrecognised status body '$body'")

        return ParseResult.Parsed(
            StatusMessage(
                validationReportId = reportId,
                correlationId = correlationId,
                codespace = codespace,
                referential = referential,
                stage = stage,
                status = status,
                publishedAt = publishedAt,
            )
        )
    }

    companion object {
        /** The stages anzu serves. Any other value is acked and logged. */
        private val STAGES = mapOf(
            "EnturValidationStagePreValidation" to Stage.PREVALIDATION,
            "EnturValidationStageExportNetexPostValidation" to Stage.POSTVALIDATION,
            "EnturValidationStageExportNetexBlocksPostValidation" to Stage.BLOCKS_POSTVALIDATION,
            "EnturValidationStageFlexPostValidation" to Stage.FLEX_POSTVALIDATION,
            "EnturValidationStageExportMergedPostValidation" to Stage.MERGED_POSTVALIDATION,
        )

        private val STATUSES = mapOf(
            "started" to RunStatus.IN_PROGRESS,
            "ok" to RunStatus.OK,
            "failed" to RunStatus.FAILED,
            /** `timeout` folds into FAILED by design, this is not a mistake */
            "timeout" to RunStatus.FAILED,
        )
    }
}
