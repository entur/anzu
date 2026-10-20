package no.entur.anzu.api

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** RFC 9457. `title` and `status` are required; `detail` is populated where it adds anything. */
data class ProblemDetailBody(
    val title: String,
    val status: Int,
    val detail: String? = null,
)

/**
 * Every failure leaves as `application/problem+json`, which is what the contract declares. The
 * catch-all makes that true of unanticipated faults too, where a container error page would not be.
 */
@RestControllerAdvice
class ProblemDetailsHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun missingHeader(e: MissingRequestHeaderException) =
        problem(HttpStatus.BAD_REQUEST, "Missing required header '${e.headerName}'.")

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) = problem(HttpStatus.NOT_FOUND, e.message)

    @ExceptionHandler(ForbiddenException::class)
    fun forbidden(e: ForbiddenException) = problem(HttpStatus.FORBIDDEN, e.message)

    @ExceptionHandler(GoneException::class)
    fun gone(e: GoneException) = problem(HttpStatus.GONE, e.message)

    @ExceptionHandler(ReportUnavailableException::class)
    fun badGateway(e: ReportUnavailableException): ResponseEntity<ProblemDetailBody> {
        logger.error("A validation report could not be read", e)
        return problem(HttpStatus.BAD_GATEWAY, "A validation report could not be read.")
    }

    @ExceptionHandler(InconsistentIndexException::class)
    fun inconsistentIndex(e: InconsistentIndexException): ResponseEntity<ProblemDetailBody> {
        logger.error("Refusing to serve an import with an inconsistent codespace. {}", e.message)
        return problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The request could not be completed because of an internal error.",
        )
    }

    /**
     * The cause is logged, never returned: it names codespaces and the state of the index. Uniform,
     * so no caller can tell two internal faults apart from the outside.
     */
    @ExceptionHandler(RuntimeException::class)
    fun unanticipated(e: RuntimeException): ResponseEntity<ProblemDetailBody> {
        logger.error("Unanticipated failure serving a validation report request", e)
        return problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The request could not be completed because of an internal error.",
        )
    }

    private fun problem(status: HttpStatus, detail: String?): ResponseEntity<ProblemDetailBody> =
        ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetailBody(status.reasonPhrase, status.value(), detail))
}
