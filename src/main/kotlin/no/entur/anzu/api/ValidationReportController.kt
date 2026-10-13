package no.entur.anzu.api

import no.entur.anzu.api.model.ValidationReportsResponse
import no.entur.anzu.api.model.ValidationRunDetail
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Paths carry no version; versioning is applied at the gateway.
 *
 * `etClientName` is accepted and not otherwise used — declaring it is what enforces the required
 * header rather than merely documenting it.
 */
@RestController
@RequestMapping("/validation-reports")
class ValidationReportController(private val service: ValidationReportService) {

    @GetMapping("/{correlationId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getValidationReports(
        @PathVariable correlationId: String,
        @RequestHeader("ET-Client-Name") etClientName: String,
    ): ValidationReportsResponse = service.reportsFor(correlationId)

    @GetMapping("/{correlationId}/{stage}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getValidationReportForStage(
        @PathVariable correlationId: String,
        @PathVariable stage: String,
        @RequestHeader("ET-Client-Name") etClientName: String,
    ): ValidationRunDetail = service.reportFor(correlationId, stage)
}
