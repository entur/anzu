package no.entur.anzu.ingest

import no.entur.anzu.index.ValidationEventRepository
import no.entur.anzu.ingest.PubSubAttributes.CORRELATION_ID
import no.entur.anzu.ingest.PubSubAttributes.DATASET_CODESPACE
import no.entur.anzu.ingest.PubSubAttributes.DATASET_REFERENTIAL
import no.entur.anzu.ingest.PubSubAttributes.VALIDATION_REPORT_ID
import no.entur.anzu.ingest.PubSubAttributes.VALIDATION_STAGE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import java.time.Instant

class ValidationStatusListenerTest {

    private val repository: ValidationEventRepository = mock()
    private val listener = ValidationStatusListener(repository, StatusMessageParser())

    private val publishedAt: Instant = Instant.parse("2026-09-25T10:34:12Z")

    private fun attributes(stage: String = "EnturValidationStagePreValidation") = mapOf(
        VALIDATION_STAGE to stage,
        VALIDATION_REPORT_ID to "tst_1",
        CORRELATION_ID to "c-1",
        DATASET_CODESPACE to "tst",
        DATASET_REFERENTIAL to "tst",
    )

    @Test
    fun `a good message is acked`() {
        assertEquals(
            Disposition.ACK,
            listener.handle("ok", attributes(), publishedAt)
        )
        verify(repository).append(any())
    }

    @Test
    fun `a malformed message is acked, not nacked`() {
        // THE bug this test exists for: nacking a deterministically bad message stops the
        // subscription for everyone behind it. It will be just as malformed next time.
        assertEquals(
            Disposition.ACK,
            listener.handle("ok", attributes() - CORRELATION_ID, publishedAt)
        )
        verify(repository, never()).append(any())
    }

    @Test
    fun `an unrecognised stage is acked`() {
        // A deployment skew, not a fault. Retrying would block the subscription.
        assertEquals(
            Disposition.ACK,
            listener.handle("ok", attributes(stage = "EnturValidationStageBrandNew"), publishedAt)
        )
        verify(repository, never()).append(any())
    }

    @Test
    fun `a lost database connection is nacked`() {
        whenever(repository.append(any()))
            .doThrow(CannotGetJdbcConnectionException("connection refused"))

        assertEquals(Disposition.NACK, listener.handle("ok", attributes(), publishedAt))
    }

    @Test
    fun `a lock timeout is nacked`() {
        whenever(repository.append(any())).doThrow(CannotAcquireLockException("lock timeout"))

        assertEquals(Disposition.NACK, listener.handle("ok", attributes(), publishedAt))
    }

    @Test
    fun `a constraint violation is acked`() {
        // The same row will violate it again. Redelivery buys nothing.
        whenever(repository.append(any()))
            .doThrow(DataIntegrityViolationException("check constraint"))

        assertEquals(Disposition.ACK, listener.handle("ok", attributes(), publishedAt))
    }

    @Test
    fun `an unclassified runtime exception is nacked`() {
        // Default to retryable: the dead letter topic is the backstop for anything this
        // classification gets wrong, and an endlessly-redelivered message is a louder failure than
        // a silently dropped one.
        whenever(repository.append(any())).doThrow(IllegalStateException("something new"))

        assertEquals(Disposition.NACK, listener.handle("ok", attributes(), publishedAt))
    }
}
