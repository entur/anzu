package no.entur.anzu.config

import no.entur.anzu.api.ReportAccess
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.entur.ror.permission.RemoteBabaRoleAssignmentExtractor
import org.rutebanken.helper.organisation.RoleAssignmentExtractor
import org.rutebanken.helper.organisation.authorization.AuthorizationService
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.web.reactive.function.client.WebClient

/**
 * The development shortcuts must be unreachable in a deployed environment.
 */
class AuthorizationConfigTest {

    /**
     * [ReportAccess] is what makes a missing authorization service a startup failure.
     *
     * The web client is a mock and the baba url is a placeholder the extractor only stores: these
     * tests assert which beans exist, and no role assignment is ever looked up. Nothing here
     * reaches a network, and the url is deliberately not of a form anything could dial.
     */
    private val runner = ApplicationContextRunner()
        .withUserConfiguration(AuthorizationConfig::class.java, ReportAccess::class.java)
        .withBean(WebClient::class.java, { mock<WebClient>() })
        .withPropertyValues("anzu.security.baba-url=no-lookup-happens-in-this-test")

    @Test
    fun `the allow-all authorization service is unreachable outside the local profile`() {
        runner
            .withPropertyValues(
                "spring.profiles.active=gcp",
                "anzu.security.authorization-service=local",
            )
            .run { context ->
                assertNotNull(
                    context.startupFailure,
                    "a deployed profile accepted the allow-all authorization service",
                )
            }
    }

    @Test
    fun `the jwt role assignment extractor is unreachable outside local and test`() {
        runner
            .withPropertyValues(
                "spring.profiles.active=gcp",
                "anzu.security.role-assignment-extractor=jwt",
            )
            .run { context ->
                assertNotNull(
                    context.startupFailure,
                    "a deployed profile accepted token-supplied role assignments",
                )
            }
    }

    @Test
    fun `a deployed profile still wires the baba-backed service`() {
        // The guards must not cost the production wiring: with no overrides, a deployed profile
        // gets an authorization service that asks baba.
        runner
            .withPropertyValues("spring.profiles.active=gcp")
            .run { context ->
                assertNotNull(context.getBean(AuthorizationService::class.java))
                assertInstanceOf(
                    RemoteBabaRoleAssignmentExtractor::class.java,
                    context.getBean(RoleAssignmentExtractor::class.java),
                )
            }
    }
}
