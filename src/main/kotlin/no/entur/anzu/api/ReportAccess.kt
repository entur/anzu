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

class InconsistentIndexException(message: String) : RuntimeException(message)

/**
 * Runs inline rather than in a `@PreAuthorize`: the codespace comes from the index, not from a path
 * parameter, so the caller cannot choose the subject of the check.
 */
@Component
class ReportAccess(private val authorizationService: AuthorizationService<String>) {

    /**
     * Authorize the caller for this import and return the codespace to report.
     *
     * @throws InconsistentIndexException if the runs disagree on the codespace
     * @throws ForbiddenException naming the correlation id, never the codespace
     */
    fun authorize(runs: List<ValidationRun>, correlationId: String): String {
        val codespaces = runs.map { it.codespace }.distinctBy { CodespaceOrganisation.of(it) }
        if (codespaces.size > 1) {
            throw InconsistentIndexException(
                "Correlation id $correlationId maps to more than one codespace: " +
                    "${codespaces.sorted()}. Refusing to serve the import."
            )
        }

        val codespace = codespaces.singleOrNull()
            ?: throw IllegalStateException("Cannot authorize an empty run list")

        if (!authorizationService.canEditRouteData(CodespaceOrganisation.of(codespace))) {
            throw ForbiddenException("Insufficient privileges for correlation id $correlationId")
        }

        return codespace
    }
}
