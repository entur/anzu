package no.entur.anzu.report

import no.entur.anzu.index.RunStatus
import no.entur.anzu.index.Stage
import no.entur.anzu.index.ValidationRun
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ReportBlobPathTest {

    private fun run(stage: Stage, referential: String, reportId: String) = ValidationRun(
        validationReportId = reportId,
        correlationId = "c-1",
        codespace = "tst",
        referential = referential,
        stage = stage,
        status = RunStatus.OK,
        startedAt = Instant.parse("2026-08-11T10:30:00Z"),
        completedAt = Instant.parse("2026-08-11T10:34:00Z"),
    )

    @Test
    fun `the layout matches what antu publishes`() {
        assertEquals(
            "reports/rb_tst/validation-report-rb_tst_20260811103000000000.json",
            ReportBlobPath.of(run(Stage.POSTVALIDATION, "rb_tst", "rb_tst_20260811103000000000"))
        )
    }

    @Test
    fun `both prefix forms resolve, from one import`() {
        // An implementation that prepends rb_ unconditionally passes every other test but this one.
        val prevalidation = run(Stage.PREVALIDATION, "tst", "tst_20260811103000000000")
        val postvalidation = run(Stage.POSTVALIDATION, "rb_tst", "rb_tst_20260811103000000000")

        assertEquals(
            "reports/tst/validation-report-tst_20260811103000000000.json",
            ReportBlobPath.of(prevalidation)
        )
        assertEquals(
            "reports/rb_tst/validation-report-rb_tst_20260811103000000000.json",
            ReportBlobPath.of(postvalidation)
        )
    }

    @Test
    fun `the path is built from the referential and never from the codespace`() {
        val odd = run(Stage.POSTVALIDATION, "rb_somethingelse", "rb_somethingelse_1")

        assertEquals(
            "reports/rb_somethingelse/validation-report-rb_somethingelse_1.json",
            ReportBlobPath.of(odd)
        )
    }

    @Test
    fun `the report id is used verbatim and never parsed`() {
        val odd = run(Stage.PREVALIDATION, "tst", "not-a-timestamp-at-all")

        assertEquals("reports/tst/validation-report-not-a-timestamp-at-all.json", ReportBlobPath.of(odd))
    }
}
