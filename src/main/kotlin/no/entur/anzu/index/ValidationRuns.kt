package no.entur.anzu.index

import java.time.Instant

/** Folds status events into one run per validation report id. */
object ValidationRuns {

    /**
     * Arrival order is never consulted: a run is derived from all its events at once, so an
     * out-of-order or redelivered event cannot change the result.
     *
     * Runs come back ordered by completion, with runs still in flight last.
     */
    fun from(events: List<ValidationEvent>): List<ValidationRun> =
        events.groupBy { it.validationReportId }
            .map { (_, ofOneRun) -> fold(ofOneRun) }
            .sortedWith(BY_COMPLETION)

    /**
     * The earliest terminal event decides the status, so a run reporting `failed` and then
     * `timeout` stays FAILED. Identity fields come from the latest event: they are the same on
     * every event in practice, and if they ever diverge the later one is the better record.
     */
    private fun fold(events: List<ValidationEvent>): ValidationRun {
        val started = events.filter { it.status == RunStatus.IN_PROGRESS }.minByOrNull { it.publishedAt }
        val terminal = events.filter { it.status.isTerminal }.minByOrNull { it.publishedAt }
        val latest = events.maxBy { it.publishedAt }

        return ValidationRun(
            validationReportId = latest.validationReportId,
            correlationId = latest.correlationId,
            codespace = latest.codespace,
            referential = latest.referential,
            stage = latest.stage,
            status = terminal?.status ?: RunStatus.IN_PROGRESS,
            startedAt = started?.publishedAt,
            completedAt = terminal?.publishedAt,
        )
    }

    private val BY_COMPLETION: Comparator<ValidationRun> =
        compareBy<ValidationRun, Instant?>(nullsLast()) { it.completedAt }
            .thenBy { it.validationReportId }
}
