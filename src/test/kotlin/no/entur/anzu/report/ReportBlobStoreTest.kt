package no.entur.anzu.report

import no.entur.anzu.index.RunStatus
import no.entur.anzu.index.Stage
import no.entur.anzu.index.ValidationRun
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.rutebanken.helper.storage.BlobStoreException
import org.rutebanken.helper.storage.repository.BlobStoreRepository
import org.rutebanken.helper.storage.repository.InMemoryBlobStoreRepository
import java.io.ByteArrayInputStream
import java.time.Instant

class ReportBlobStoreTest {

    private val repository = InMemoryBlobStoreRepository(mutableMapOf())
        .apply { setContainerName("antu") }
    private val store = ReportBlobStore(repository)

    private fun run(referential: String = "tst", reportId: String = "tst_1") = ValidationRun(
        validationReportId = reportId,
        correlationId = "c-1",
        codespace = "tst",
        referential = referential,
        stage = Stage.PREVALIDATION,
        status = RunStatus.OK,
        startedAt = Instant.parse("2026-08-11T10:30:00Z"),
        completedAt = Instant.parse("2026-08-11T10:34:00Z"),
    )

    @Test
    fun `a present blob comes back as its bytes`() {
        val expected = """{"codespace":"tst"}""".toByteArray()
        repository.uploadBlob(
            "reports/tst/validation-report-tst_1.json",
            ByteArrayInputStream(expected),
        )

        val result = store.read(run())

        assertArrayEquals(expected, assertInstanceOf(BlobResult.Found::class.java, result).bytes)
    }

    @Test
    fun `a missing blob is Absent`() {
        assertInstanceOf(BlobResult.Absent::class.java, store.read(run()))
    }

    @Test
    fun `a storage failure is Failed, and must not collapse into Absent`() {
        // Reporting a transient fault as 410 would tell a partner their report is permanently gone
        // while it sits in the bucket. That case is 502.
        val failing: BlobStoreRepository = mock()
        whenever(failing.getBlob(any())).doThrow(BlobStoreException("backend error"))

        assertInstanceOf(BlobResult.Failed::class.java, ReportBlobStore(failing).read(run()))
    }

    @Test
    fun `an unexpected runtime failure is also Failed`() {
        // Misconfigured IAM must not read as "nothing is there".
        val failing: BlobStoreRepository = mock()
        whenever(failing.getBlob(any())).doThrow(IllegalStateException("permission denied"))

        assertInstanceOf(BlobResult.Failed::class.java, ReportBlobStore(failing).read(run()))
    }

    @Test
    fun `the blob is read from the referential path, not the codespace path`() {
        repository.uploadBlob(
            "reports/rb_tst/validation-report-rb_tst_2.json",
            ByteArrayInputStream(ByteArray(1)),
        )

        val result = store.read(run(referential = "rb_tst", reportId = "rb_tst_2"))

        assertInstanceOf(BlobResult.Found::class.java, result)
    }
}
