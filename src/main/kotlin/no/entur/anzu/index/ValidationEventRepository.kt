package no.entur.anzu.index

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp

@Repository
class ValidationEventRepository(private val jdbc: JdbcClient) {

    /**
     * Append one status event. Redelivery is dropped by the unique key, and would be harmless
     * anyway — the fold ignores duplicates.
     */
    fun append(event: ValidationEvent) {
        jdbc.sql(
            """
            insert into validation_event
                (validation_report_id, correlation_id, codespace, referential, stage, status,
                 published_at, received_at)
            values (:reportId, :correlationId, :codespace, :referential, :stage, :status,
                    :publishedAt, now())
            on conflict (validation_report_id, status, published_at) do nothing
            """
        )
            .param("reportId", event.validationReportId)
            .param("correlationId", event.correlationId)
            .param("codespace", event.codespace)
            .param("referential", event.referential)
            .param("stage", event.stage.name)
            .param("status", event.status.name)
            .param("publishedAt", Timestamp.from(event.publishedAt))
            .update()
    }

    /**
     * Every run of one import, folded from its events.
     *
     * There is deliberately no query by stage. The per-stage endpoint narrows in memory, because
     * authorization must run before the stage is considered and needs the earliest run across all
     * stages.
     */
    fun findRuns(correlationId: String): List<ValidationRun> =
        ValidationRuns.from(
            jdbc.sql("select * from validation_event where correlation_id = :correlationId")
                .param("correlationId", correlationId)
                .query(::toEvent)
                .list()
        )

    private fun toEvent(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) = ValidationEvent(
        validationReportId = rs.getString("validation_report_id"),
        correlationId = rs.getString("correlation_id"),
        codespace = rs.getString("codespace"),
        referential = rs.getString("referential"),
        stage = Stage.valueOf(rs.getString("stage")),
        status = RunStatus.valueOf(rs.getString("status")),
        publishedAt = rs.getTimestamp("published_at").toInstant(),
    )
}
