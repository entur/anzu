package no.entur.anzu.api

import no.entur.anzu.auth.CodespaceOrganisation
import no.entur.anzu.index.ValidationRun
import org.rutebanken.helper.organisation.authorization.AuthorizationService
import org.springframework.stereotype.Component

class ForbiddenException(message: String) : RuntimeException(message)

class NotFoundException(message: String) : RuntimeException(message)

class GoneException(message: String) : RuntimeException(message)

class ReportUnavailableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Runs inline rather than in a `@PreAuthorize`: the codespace comes from the index, not from a path
 * parameter, so the caller cannot choose the subject of the check.
 */
@Component
class ReportAccess(private val authorizationService: AuthorizationService<String>) {

    /**
     * Authorize the caller for this import and return the codespace to report.
     *
     * The codespace is the **earliest** run's. Runs under one correlation id need not share one, because
     * authorizing against a later one would authorize a migrated codespace against a delivery made
     * to the original provider.
     *
     * @throws ForbiddenException naming the correlation id, never the codespace
     */
    fun authorize(runs: List<ValidationRun>, correlationId: String): String {
        val codespace = runs.firstOrNull()?.codespace
            ?: throw IllegalStateException("Cannot authorize an empty run list")

        if (!authorizationService.canEditRouteData(CodespaceOrganisation.of(codespace))) {
            throw ForbiddenException("Insufficient privileges for correlation id $correlationId")
        }

        return codespace
    }
}
