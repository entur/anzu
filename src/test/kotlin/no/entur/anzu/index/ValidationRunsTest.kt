package no.entur.anzu.index

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ValidationRunsTest {

    private val t0: Instant = Instant.parse("2026-09-25T10:30:00Z")
    private val t1: Instant = Instant.parse("2026-09-25T10:34:12Z")

    private fun event(
        status: RunStatus,
        publishedAt: Instant,
        reportId: String = "tst_1",
        stage: Stage = Stage.PREVALIDATION,
        referential: String = "tst",
    ) = ValidationEvent(
        validationReportId = reportId,
        correlationId = "c-1",
        codespace = "tst",
        referential = referential,
        stage = stage,
        status = status,
        publishedAt = publishedAt,
    )

    @Test
    fun `a start and a terminal event fold into one run with both timestamps`() {
        val run = ValidationRuns.from(
            listOf(event(RunStatus.IN_PROGRESS, t0), event(RunStatus.OK, t1))
        ).single()

        assertEquals(RunStatus.OK, run.status)
        assertEquals(t0, run.startedAt)
        assertEquals(t1, run.completedAt)
    }

    @Test
    fun `arrival order does not matter`() {
        val inOrder = listOf(event(RunStatus.IN_PROGRESS, t0), event(RunStatus.OK, t1))

        assertEquals(ValidationRuns.from(inOrder), ValidationRuns.from(inOrder.reversed()))
    }

    @Test
    fun `duplicate events change nothing`() {
        val once = listOf(event(RunStatus.IN_PROGRESS, t0), event(RunStatus.OK, t1))
        val thrice = once + once + once

        assertEquals(ValidationRuns.from(once), ValidationRuns.from(thrice))
    }

    @Test
    fun `a run with no start event has a null startedAt`() {
        val run = ValidationRuns.from(listOf(event(RunStatus.FAILED, t1))).single()

        assertEquals(RunStatus.FAILED, run.status)
        assertEquals(null, run.startedAt)
        assertEquals(t1, run.completedAt)
    }

    @Test
    fun `a run with only a start event is still in progress`() {
        val run = ValidationRuns.from(listOf(event(RunStatus.IN_PROGRESS, t0))).single()

        assertEquals(RunStatus.IN_PROGRESS, run.status)
        assertEquals(t0, run.startedAt)
        assertEquals(null, run.completedAt)
    }

    @Test
    fun `the earliest terminal event decides the status`() {
        // failed then timeout, both FAILED on our side, but the rule has to be defined.
        val run = ValidationRuns.from(
            listOf(event(RunStatus.OK, t1), event(RunStatus.FAILED, t1.plusSeconds(60)))
        ).single()

        assertEquals(RunStatus.OK, run.status)
        assertEquals(t1, run.completedAt)
    }

    @Test
    fun `runs come back ordered by completion, with an in-progress run last`() {
        val runs = ValidationRuns.from(
            listOf(
                event(RunStatus.OK, t0.plusSeconds(600), reportId = "tst_post", stage = Stage.POSTVALIDATION),
                event(RunStatus.IN_PROGRESS, t0, reportId = "tst_prog", stage = Stage.FLEX_POSTVALIDATION),
                event(RunStatus.FAILED, t0.plusSeconds(60), reportId = "tst_pre"),
            )
        )

        assertEquals(
            listOf(Stage.PREVALIDATION, Stage.POSTVALIDATION, Stage.FLEX_POSTVALIDATION),
            runs.map { it.stage }
        )
    }

    @Test
    fun `two report ids are two runs`() {
        val runs = ValidationRuns.from(
            listOf(
                event(RunStatus.OK, t1, reportId = "tst_1"),
                event(RunStatus.OK, t1.plusSeconds(60), reportId = "rb_tst_2", stage = Stage.POSTVALIDATION),
            )
        )

        assertEquals(2, runs.size)
    }

    @Test
    fun `the referential is kept verbatim, prefixed or not`() {
        val runs = ValidationRuns.from(
            listOf(
                event(RunStatus.OK, t0, reportId = "tst_pre", referential = "tst"),
                event(RunStatus.OK, t1, reportId = "tst_post", referential = "rb_tst"),
            )
        )

        assertEquals(listOf("tst", "rb_tst"), runs.map { it.referential })
    }

    @Test
    fun `no events is no runs`() {
        assertEquals(emptyList<ValidationRun>(), ValidationRuns.from(emptyList()))
    }
}
