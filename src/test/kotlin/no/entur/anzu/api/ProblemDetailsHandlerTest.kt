package no.entur.anzu.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class ProblemDetailsHandlerTest {

    private val handler = ProblemDetailsHandler()

    @Test
    fun `every problem carries problem+json with a title and a status`() {
        val responses = listOf(
            handler.notFound(NotFoundException("Correlation id not found")),
            handler.forbidden(ForbiddenException("Insufficient privileges for correlation id c-1")),
            handler.gone(GoneException("No report available")),
            handler.badGateway(ReportUnavailableException("boom")),
            handler.unanticipated(IllegalStateException("boom")),
        )

        responses.forEach { response ->
            assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
            val body = response.body!!
            assertEquals(response.statusCode.value(), body.status)
            assertTrue(body.title.isNotBlank())
        }
    }

    @Test
    fun `a 5xx never repeats the cause to the caller`() {
        val body = handler.unanticipated(IllegalStateException("codespace rut is unknown")).body!!

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), body.status)
        assertFalse(body.detail!!.contains("rut"))
    }

    @Test
    fun `a 502 never repeats the cause to the caller`() {
        val body = handler.badGateway(ReportUnavailableException("bucket anzu-prod denied")).body!!

        assertEquals(HttpStatus.BAD_GATEWAY.value(), body.status)
        assertFalse(body.detail!!.contains("anzu-prod"))
    }

    @Test
    fun `a 403 detail names the correlation id and not a codespace`() {
        val body = handler.forbidden(
            ForbiddenException("Insufficient privileges for correlation id c-1")
        ).body!!

        assertTrue(body.detail!!.contains("c-1"))
    }
}
