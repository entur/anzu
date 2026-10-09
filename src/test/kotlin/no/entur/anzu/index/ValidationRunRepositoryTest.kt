package no.entur.anzu.index

import no.entur.anzu.PostgresTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant

@SpringBootTest
@Import(PostgresTestBase::class)
class ValidationRunRepositoryTest {

    @Autowired private lateinit var repository: ValidationRunRepository
    @Autowired private lateinit var jdbc: JdbcClient

    private val t0: Instant = Instant.parse("2026-09-25T10:30:00Z")

    @BeforeEach
    fun clear() {
        jdbc.sql("delete from validation_run").update()
    }

    @Test
    fun `runs come back ordered by completion, with an in-progress run last`() {
        insert("tst_post", "c-1", "POSTVALIDATION", "OK", t0.plusSeconds(600))
        insert("tst_prog", "c-1", "FLEX_POSTVALIDATION", "IN_PROGRESS", null)
        insert("tst_pre", "c-1", "PREVALIDATION", "FAILED", t0.plusSeconds(60))

        val runs = repository.findByCorrelationId("c-1")

        assertEquals(
            listOf(Stage.PREVALIDATION, Stage.POSTVALIDATION, Stage.FLEX_POSTVALIDATION),
            runs.map { it.stage }
        )
    }

    @Test
    fun `the referential is read back verbatim, prefixed or not`() {
        insert("tst_pre", "c-2", "PREVALIDATION", "OK", t0, referential = "tst")
        insert("tst_post", "c-2", "POSTVALIDATION", "OK", t0.plusSeconds(60), referential = "rb_tst")

        assertEquals(listOf("tst", "rb_tst"), repository.findByCorrelationId("c-2").map { it.referential })
    }

    @Test
    fun `an unknown correlation id yields no rows`() {
        assertEquals(emptyList<ValidationRun>(), repository.findByCorrelationId("nope"))
    }

    private fun insert(
        reportId: String,
        correlationId: String,
        stage: String,
        status: String,
        completedAt: Instant?,
        codespace: String = "tst",
        referential: String = "rb_tst",
    ) {
        jdbc.sql(
            """
            insert into validation_run
                (validation_report_id, correlation_id, codespace, referential, stage, status,
                 started_at, completed_at, updated_at)
            values (:reportId, :correlationId, :codespace, :referential, :stage, :status,
                    :startedAt, :completedAt, now())
            """
        )
            .param("reportId", reportId)
            .param("correlationId", correlationId)
            .param("codespace", codespace)
            .param("referential", referential)
            .param("stage", stage)
            .param("status", status)
            .param("startedAt", Timestamp.from(t0))
            .param("completedAt", completedAt?.let { Timestamp.from(it) })
            .update()
    }
}
