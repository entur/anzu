package no.entur.anzu.ingest

import no.entur.anzu.index.RunStatus
import no.entur.anzu.index.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class StatusMessageTest {

    private val publishedAt: Instant = Instant.parse("2026-09-25T10:34:12Z")

    @Test
    fun `a message maps onto an event field for field`() {
        val event = StatusMessage(
            validationReportId = "tst_1",
            correlationId = "c-1",
            codespace = "tst",
            referential = "rb_tst",
            stage = Stage.POSTVALIDATION,
            status = RunStatus.OK,
            publishedAt = publishedAt,
        ).toEvent()

        assertEquals("tst_1", event.validationReportId)
        assertEquals("c-1", event.correlationId)
        assertEquals("tst", event.codespace)
        assertEquals("rb_tst", event.referential)
        assertEquals(Stage.POSTVALIDATION, event.stage)
        assertEquals(RunStatus.OK, event.status)
        assertEquals(publishedAt, event.publishedAt)
    }
}
