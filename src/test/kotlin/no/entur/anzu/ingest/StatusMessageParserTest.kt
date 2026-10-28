package no.entur.anzu.ingest

import no.entur.anzu.index.RunStatus
import no.entur.anzu.index.Stage
import no.entur.anzu.ingest.PubSubAttributes.CORRELATION_ID
import no.entur.anzu.ingest.PubSubAttributes.DATASET_CODESPACE
import no.entur.anzu.ingest.PubSubAttributes.DATASET_REFERENTIAL
import no.entur.anzu.ingest.PubSubAttributes.VALIDATION_REPORT_ID
import no.entur.anzu.ingest.PubSubAttributes.VALIDATION_STAGE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

class StatusMessageParserTest {

    private val parser = StatusMessageParser()
    private val publishedAt: Instant = Instant.parse("2026-09-25T10:34:12Z")

    private fun attributes(
        stage: String = "EnturValidationStagePreValidation",
        reportId: String = "tst_20260811103000000000",
        correlationId: String = "c-1",
        codespace: String = "tst",
        referential: String = "tst",
    ) = mapOf(
        VALIDATION_STAGE to stage,
        VALIDATION_REPORT_ID to reportId,
        CORRELATION_ID to correlationId,
        DATASET_CODESPACE to codespace,
        DATASET_REFERENTIAL to referential,
    )

    private fun parsed(body: String, attrs: Map<String, String> = attributes()): StatusMessage =
        (parser.parse(body, attrs, publishedAt) as ParseResult.Parsed).message

    @Test
    fun `the attribute keys are the ones on the wire`() {
        assertEquals("EnturValidationCorrelationId", CORRELATION_ID)
        assertEquals("EnturDatasetCodespace", DATASET_CODESPACE)
        assertEquals("EnturDatasetReferential", DATASET_REFERENTIAL)
        assertEquals("EnturValidationReportId", VALIDATION_REPORT_ID)
        assertEquals("EnturValidationStage", VALIDATION_STAGE)
    }

    @Test
    fun `started maps to IN_PROGRESS and keeps the publish time`() {
        val message = parsed("started")

        assertEquals(RunStatus.IN_PROGRESS, message.status)
        assertEquals(publishedAt, message.publishedAt)
    }

    @Test
    fun `ok maps to OK`() {
        assertEquals(RunStatus.OK, parsed("ok").status)
    }

    @Test
    fun `failed maps to FAILED`() {
        assertEquals(RunStatus.FAILED, parsed("failed").status)
    }

    @Test
    fun `timeout folds into FAILED`() {
        assertEquals(RunStatus.FAILED, parsed("timeout").status)
    }

    @Test
    fun `every stage constant maps to its external stage`() {
        val expected = mapOf(
            "EnturValidationStagePreValidation" to Stage.PREVALIDATION,
            "EnturValidationStageNightlyValidation" to Stage.PREVALIDATION,
            "EnturValidationStageExportNetexPostValidation" to Stage.POSTVALIDATION,
            "EnturValidationStageExportNetexBlocksPostValidation" to Stage.BLOCKS_POSTVALIDATION,
            "EnturValidationStageFlexPostValidation" to Stage.FLEX_POSTVALIDATION,
            "EnturValidationStageExportMergedPostValidation" to Stage.MERGED_POSTVALIDATION,
        )

        expected.forEach { (constant, stage) ->
            assertEquals(stage, parsed("ok", attributes(stage = constant)).stage, constant)
        }
    }

    @Test
    fun `an unrecognised stage is unprocessable, not retryable`() {
        // a deployment skew, not a fault. Acked and logged, never nacked.
        assertInstanceOf(
            ParseResult.Unprocessable::class.java,
            parser.parse("ok", attributes(stage = "EnturValidationStageSomethingNew"), publishedAt)
        )
    }

    @Test
    fun `an unrecognised status body is unprocessable`() {
        assertInstanceOf(
            ParseResult.Unprocessable::class.java,
            parser.parse("sideways", attributes(), publishedAt)
        )
    }

    @Test
    fun `a message missing any required attribute is unprocessable`() {
        listOf(
            CORRELATION_ID,
            VALIDATION_REPORT_ID,
            VALIDATION_STAGE,
            DATASET_CODESPACE,
            DATASET_REFERENTIAL,
        ).forEach { missing ->
            assertInstanceOf(
                ParseResult.Unprocessable::class.java,
                parser.parse("ok", attributes() - missing, publishedAt),
                "missing $missing"
            )
        }
    }

    @Test
    fun `the referential is kept verbatim, prefixed or not`() {
        // prevalidation files reports under the bare codespace, later stages under rb_
        assertEquals("tst", parsed("ok", attributes(referential = "tst")).referential)
        assertEquals("rb_tst", parsed("ok", attributes(referential = "rb_tst")).referential)
    }
}
