package no.entur.anzu.report

import no.entur.anzu.index.ValidationRun
import org.rutebanken.helper.storage.repository.BlobStoreRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * The outcome of looking for one run's report.
 *
 * [Absent] and [Failed] are deliberately separate: a missing blob is a fact about the report, a
 * failed read is a fact about us, and reporting the second as the first tells a partner their
 * report is permanently gone while it sits in the bucket.
 */
sealed interface BlobResult {
    class Found(val bytes: ByteArray) : BlobResult

    data object Absent : BlobResult

    data class Failed(val cause: Exception) : BlobResult
}

/**
 * Reads published validation reports out of antu's bucket. Read-only; anzu never writes there.
 *
 * The `.status` sidecar beside each report is not consulted. It records that a terminal status was
 * sent, not that a report is readable, and it is written on the timeout path too, where no report
 * exists at all.
 */
@Component
class ReportBlobStore(private val reports: BlobStoreRepository) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun read(run: ValidationRun): BlobResult {
        val path = ReportBlobPath.of(run)
        return try {
            // The repository contract is null for absent and an exception for anything else.
            reports.getBlob(path)
                ?.use { BlobResult.Found(it.readBytes()) }
                ?: BlobResult.Absent
        } catch (e: RuntimeException) {
            logger.error("Could not read the report at '{}'", path, e)
            BlobResult.Failed(e)
        }
    }
}
