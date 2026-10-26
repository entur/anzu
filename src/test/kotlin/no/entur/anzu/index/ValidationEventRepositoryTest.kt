package no.entur.anzu.index

import no.entur.anzu.PostgresTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

/** Round-trip and the unique key. The folding rules are covered by [ValidationRunsTest]. */
@SpringBootTest
@Import(PostgresTestBase::class)
@ActiveProfiles("test")
class ValidationEventRepositoryTest {

    @Autowired private lateinit var repository: ValidationEventRepository
    @Autowired private lateinit var jdbc: JdbcClient

    private val t0: Instant = Instant.parse("2026-09-25T10:30:00Z")
    private val t1: Instant = Instant.parse("2026-09-25T10:34:12Z")

    @BeforeEach
    fun clear() {
        jdbc.sql("delete from validation_event").update()
    }

    private fun event(status: RunStatus, publishedAt: Instant, reportId: String = "tst_1") =
        ValidationEvent(
            validationReportId = reportId,
            correlationId = "c-1",
            codespace = "tst",
            referential = "rb_tst",
            stage = Stage.PREVALIDATION,
            status = status,
            publishedAt = publishedAt,
        )

    private fun eventCount(): Int =
        jdbc.sql("select count(*) from validation_event").query(Int::class.java).single()

    @Test
    fun `events are appended and fold back into a run`() {
        repository.append(event(RunStatus.IN_PROGRESS, t0))
        repository.append(event(RunStatus.OK, t1))

        val run = repository.findRuns("c-1").single()

        assertEquals("tst_1", run.validationReportId)
        assertEquals("tst", run.codespace)
        assertEquals("rb_tst", run.referential)
        assertEquals(Stage.PREVALIDATION, run.stage)
        assertEquals(RunStatus.OK, run.status)
        assertEquals(t0, run.startedAt)
        assertEquals(t1, run.completedAt)
    }

    @Test
    fun `redelivery of the same event inserts one row`() {
        val started = event(RunStatus.IN_PROGRESS, t0)

        repository.append(started)
        repository.append(started)
        repository.append(started)

        assertEquals(1, eventCount())
    }

    @Test
    fun `two events for one run are two rows`() {
        repository.append(event(RunStatus.IN_PROGRESS, t0))
        repository.append(event(RunStatus.OK, t1))

        assertEquals(2, eventCount())
        assertEquals(1, repository.findRuns("c-1").size)
    }

    @Test
    fun `an unknown correlation id yields no runs`() {
        assertEquals(emptyList<ValidationRun>(), repository.findRuns("nope"))
    }
}
