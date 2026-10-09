package no.entur.anzu.index

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class ValidationRunRepository(private val jdbc: JdbcClient) {

    /**
     * Every run of one import, ordered by completion with runs still in flight last.
     *
     * Ordering is on `completed_at`.
     *
     * There is deliberately no query by stage. The per-stage endpoint narrows in memory, because
     * authorization must run before the stage is considered and needs the earliest run across all
     * stages.
     */
    fun findByCorrelationId(correlationId: String): List<ValidationRun> =
        jdbc.sql(
            """
            select * from validation_run
            where correlation_id = :correlationId
            order by completed_at asc nulls last, id asc
            """
        )
            .param("correlationId", correlationId)
            .query(::toRun)
            .list()

    private fun toRun(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) = ValidationRun(
        validationReportId = rs.getString("validation_report_id"),
        correlationId = rs.getString("correlation_id"),
        codespace = rs.getString("codespace"),
        referential = rs.getString("referential"),
        stage = Stage.valueOf(rs.getString("stage")),
        status = RunStatus.valueOf(rs.getString("status")),
        startedAt = rs.getTimestamp("started_at")?.toInstant(),
        completedAt = rs.getTimestamp("completed_at")?.toInstant(),
    )
}
