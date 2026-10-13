package no.entur.anzu.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import no.entur.anzu.api.model.RunStatusResponse
import no.entur.anzu.api.model.ValidationEntry
import no.entur.anzu.api.model.ValidationReportsResponse
import no.entur.anzu.api.model.ValidationRunDetail
import no.entur.anzu.api.model.ValidationRunSummary
import no.entur.anzu.index.Stage
import no.entur.anzu.index.ValidationEventRepository
import no.entur.anzu.index.ValidationRun
import no.entur.anzu.report.BlobResult
import no.entur.anzu.report.ReportBlobStore
import no.entur.anzu.report.ReportMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.ZoneOffset

@Service
class ValidationReportService(
    private val repository: ValidationEventRepository,
    private val blobs: ReportBlobStore,
    private val mapper: ReportMapper,
    private val access: ReportAccess,
    @Value("\${anzu.blobstore.fetch-parallelism:8}") private val parallelism: Int,
) {

    private val dispatcher = Dispatchers.IO.limitedParallelism(parallelism)

    /** One element per run of the import, in completion order. */
    fun reportsFor(correlationId: String): ValidationReportsResponse {
        val runs = repository.findRuns(correlationId)
        if (runs.isEmpty()) {
            throw NotFoundException("Correlation id not found")
        }

        val codespace = access.authorize(runs, correlationId)
        val entriesPerRun = fetchAll(runs)

        return ValidationReportsResponse(
            correlationId = correlationId,
            codespace = codespace,
            validationRuns = runs.mapIndexed { index, run ->
                ValidationRunSummary(
                    stage = run.stage.name,
                    status = RunStatusResponse.valueOf(run.status.name),
                    startedAt = run.startedAt?.atOffset(ZoneOffset.UTC),
                    completedAt = run.completedAt?.atOffset(ZoneOffset.UTC),
                    entries = entriesPerRun[index],
                )
            },
        )
    }

    /**
     * One run of the import.
     *
     * Authorization runs **before** the stage is resolved. Backwards, and an unauthorized caller
     * holding a correlation id could map out which steps of someone else's import ran by reading
     * 404 against 403 across the stage segments.
     */
    fun reportFor(correlationId: String, stageSegment: String): ValidationRunDetail {
        val runs = repository.findRuns(correlationId)
        if (runs.isEmpty()) {
            throw NotFoundException("Correlation id not found")
        }

        val codespace = access.authorize(runs, correlationId)

        val stage = Stage.fromSegment(stageSegment)
            ?: throw NotFoundException("No such validation stage")

        // Newest by completion; nulls last prefers a run that has a report over one still in flight.
        val run = runs.filter { it.stage == stage }
            .maxByOrNull { it.completedAt?.toEpochMilli() ?: Long.MIN_VALUE }
            ?: throw NotFoundException("No $stageSegment run for this correlation id")

        val entries = when {
            !run.status.isTerminal -> null
            else -> when (val blob = blobs.read(run)) {
                is BlobResult.Found -> mapper.toEntries(blob.bytes)
                is BlobResult.Failed -> throw ReportUnavailableException(
                    "Could not read the report for $stageSegment", blob.cause
                )
                // Terminal with no blob: aged out of retention, or the run timed out and no report
                // was ever written. Both are gone.
                BlobResult.Absent -> throw GoneException(
                    "No report is available for the $stageSegment run of this import"
                )
            }
        }

        return ValidationRunDetail(
            correlationId = correlationId,
            codespace = codespace,
            stage = stage.name,
            status = RunStatusResponse.valueOf(run.status.name),
            startedAt = run.startedAt?.atOffset(ZoneOffset.UTC),
            completedAt = run.completedAt?.atOffset(ZoneOffset.UTC),
            entries = entries,
        )
    }

    /**
     * Every terminal run's report, fetched concurrently, one slot per run positionally.
     *
     * A read failure fails the whole request rather than returning a partial list that looks
     * authoritative. An absent blob does not: the collection cannot withhold the runs that resolved.
     */
    private fun fetchAll(runs: List<ValidationRun>): List<List<ValidationEntry>?> =
        runBlocking(dispatcher) {
            coroutineScope {
                runs.map { run ->
                    async {
                        if (!run.status.isTerminal) {
                            null
                        } else {
                            when (val blob = blobs.read(run)) {
                                is BlobResult.Found -> mapper.toEntries(blob.bytes)
                                BlobResult.Absent -> null
                                is BlobResult.Failed -> throw ReportUnavailableException(
                                    "Could not read the report for ${run.stage.segment}", blob.cause
                                )
                            }
                        }
                    }
                }.awaitAll()
            }
        }
}
