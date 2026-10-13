package no.entur.anzu.api

import no.entur.anzu.api.model.RunStatusResponse
import no.entur.anzu.index.RunStatus
import no.entur.anzu.index.Stage
import no.entur.anzu.index.ValidationEventRepository
import no.entur.anzu.index.ValidationRun
import no.entur.anzu.report.BlobResult
import no.entur.anzu.report.ReportBlobStore
import no.entur.anzu.report.ReportMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import java.time.Instant

class ValidationReportServiceTest {

    private val repository: ValidationEventRepository = mock()
    private val blobs: ReportBlobStore = mock()
    private val access: ReportAccess = mock()
    private val mapper = ReportMapper(ObjectMapper())
    private val service = ValidationReportService(repository, blobs, mapper, access, parallelism = 4)

    private val t0: Instant = Instant.parse("2026-09-25T10:30:00Z")

    private fun run(stage: Stage, status: RunStatus, completedAt: Instant?) = ValidationRun(
        validationReportId = "id-${stage.name}",
        correlationId = "c-1",
        codespace = "tst",
        referential = if (stage == Stage.PREVALIDATION) "tst" else "rb_tst",
        stage = stage,
        status = status,
        startedAt = t0,
        completedAt = completedAt,
    )

    private val cleanBlob = """
        {"codespace":"tst","validationReportId":"id","validationReportEntries":[],
         "numberOfValidationEntriesPerRule":{}}
    """.trimIndent().toByteArray()

    private fun grantAccess() {
        whenever(access.authorize(any(), any())).thenReturn("tst")
    }

    // ---- collection ----

    @Test
    fun `an unknown correlation id is a 404`() {
        whenever(repository.findRuns("nope")).thenReturn(emptyList())

        assertThrows<NotFoundException> { service.reportsFor("nope") }
    }

    @Test
    fun `a clean run yields an empty entry list and an absent blob yields null`() {
        // [] means the run found nothing; null means no report is available.
        val pre = run(Stage.PREVALIDATION, RunStatus.OK, t0.plusSeconds(60))
        val post = run(Stage.POSTVALIDATION, RunStatus.FAILED, t0.plusSeconds(600))
        whenever(repository.findRuns("c-1")).thenReturn(listOf(pre, post))
        grantAccess()
        whenever(blobs.read(pre)).thenReturn(BlobResult.Found(cleanBlob))
        whenever(blobs.read(post)).thenReturn(BlobResult.Absent)

        val response = service.reportsFor("c-1")

        assertEquals("tst", response.codespace)
        assertEquals("c-1", response.correlationId)
        assertEquals(emptyList<Any>(), response.validationRuns[0].entries)
        assertNull(response.validationRuns[1].entries)
    }

    @Test
    fun `an in-progress run is never fetched`() {
        val inProgress = run(Stage.FLEX_POSTVALIDATION, RunStatus.IN_PROGRESS, null)
        whenever(repository.findRuns("c-1")).thenReturn(listOf(inProgress))
        grantAccess()

        val response = service.reportsFor("c-1")

        assertNull(response.validationRuns.single().entries)
        verify(blobs, never()).read(any())
    }

    @Test
    fun `a read failure fails the whole collection rather than returning a partial list`() {
        val pre = run(Stage.PREVALIDATION, RunStatus.OK, t0.plusSeconds(60))
        whenever(repository.findRuns("c-1")).thenReturn(listOf(pre))
        grantAccess()
        whenever(blobs.read(pre)).thenReturn(BlobResult.Failed(RuntimeException("503")))

        assertThrows<ReportUnavailableException> { service.reportsFor("c-1") }
    }

    @Test
    fun `runs keep the repository's completion order`() {
        val pre = run(Stage.PREVALIDATION, RunStatus.OK, t0.plusSeconds(60))
        val post = run(Stage.POSTVALIDATION, RunStatus.OK, t0.plusSeconds(600))
        val prog = run(Stage.FLEX_POSTVALIDATION, RunStatus.IN_PROGRESS, null)
        whenever(repository.findRuns("c-1")).thenReturn(listOf(pre, post, prog))
        grantAccess()
        whenever(blobs.read(any())).thenReturn(BlobResult.Found(cleanBlob))

        val stages = service.reportsFor("c-1").validationRuns.map { it.stage }

        assertEquals(listOf("PREVALIDATION", "POSTVALIDATION", "FLEX_POSTVALIDATION"), stages)
    }

    // ---- per stage ----

    @Test
    fun `an unknown correlation id is a 404 before any stage is considered`() {
        whenever(repository.findRuns("nope")).thenReturn(emptyList())

        assertThrows<NotFoundException> { service.reportFor("nope", "prevalidation") }
    }

    @Test
    fun `authorization runs before the stage is resolved`() {
        // Every segment must be 403, never 404: otherwise an unauthorized caller can map out which
        // steps of someone else's import ran.
        val pre = run(Stage.PREVALIDATION, RunStatus.OK, t0.plusSeconds(60))
        whenever(repository.findRuns("c-1")).thenReturn(listOf(pre))
        whenever(access.authorize(any(), any()))
            .thenThrow(ForbiddenException("Insufficient privileges for correlation id c-1"))

        assertThrows<ForbiddenException> { service.reportFor("c-1", "prevalidation") }
        assertThrows<ForbiddenException> { service.reportFor("c-1", "merged-postvalidation") }
        assertThrows<ForbiddenException> { service.reportFor("c-1", "not-a-stage") }
    }

    @Test
    fun `a stage that did not run is a 404`() {
        val pre = run(Stage.PREVALIDATION, RunStatus.OK, t0.plusSeconds(60))
        whenever(repository.findRuns("c-1")).thenReturn(listOf(pre))
        grantAccess()

        assertThrows<NotFoundException> { service.reportFor("c-1", "merged-postvalidation") }
    }

    @Test
    fun `a segment that is not a stage is a 404, not a 400`() {
        whenever(repository.findRuns("c-1"))
            .thenReturn(listOf(run(Stage.PREVALIDATION, RunStatus.OK, t0)))
        grantAccess()

        assertThrows<NotFoundException> { service.reportFor("c-1", "sideways") }
    }

    @Test
    fun `every stage segment resolves to its stage`() {
        val all = Stage.entries.map { run(it, RunStatus.OK, t0.plusSeconds(60)) }
        whenever(repository.findRuns("c-1")).thenReturn(all)
        grantAccess()
        whenever(blobs.read(any())).thenReturn(BlobResult.Found(cleanBlob))

        Stage.entries.forEach { stage ->
            assertEquals(stage.name, service.reportFor("c-1", stage.segment).stage, stage.segment)
        }
    }

    @Test
    fun `a terminal run whose blob is absent is 410`() {
        val pre = run(Stage.PREVALIDATION, RunStatus.FAILED, t0.plusSeconds(60))
        whenever(repository.findRuns("c-1")).thenReturn(listOf(pre))
        grantAccess()
        whenever(blobs.read(pre)).thenReturn(BlobResult.Absent)

        assertThrows<GoneException> { service.reportFor("c-1", "prevalidation") }
    }

    @Test
    fun `an in-progress run is 200 with no entries, and not 410`() {
        // Nothing is gone — it has not been written yet.
        val prog = run(Stage.PREVALIDATION, RunStatus.IN_PROGRESS, null)
        whenever(repository.findRuns("c-1")).thenReturn(listOf(prog))
        grantAccess()

        val response = service.reportFor("c-1", "prevalidation")

        assertEquals(RunStatusResponse.IN_PROGRESS, response.status)
        assertNull(response.entries)
    }

    @Test
    fun `a read failure is 502, and not 410`() {
        val pre = run(Stage.PREVALIDATION, RunStatus.OK, t0.plusSeconds(60))
        whenever(repository.findRuns("c-1")).thenReturn(listOf(pre))
        grantAccess()
        whenever(blobs.read(pre)).thenReturn(BlobResult.Failed(RuntimeException("503")))

        assertThrows<ReportUnavailableException> { service.reportFor("c-1", "prevalidation") }
    }

    @Test
    fun `the single-run body carries the codespace, which the collection element does not`() {
        val pre = run(Stage.PREVALIDATION, RunStatus.OK, t0.plusSeconds(60))
        whenever(repository.findRuns("c-1")).thenReturn(listOf(pre))
        grantAccess()
        whenever(blobs.read(pre)).thenReturn(BlobResult.Found(cleanBlob))

        val response = service.reportFor("c-1", "prevalidation")

        assertEquals("tst", response.codespace)
        assertEquals("c-1", response.correlationId)
    }
}
