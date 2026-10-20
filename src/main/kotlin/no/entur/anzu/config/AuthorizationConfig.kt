package no.entur.anzu.config

import no.entur.anzu.auth.CodespaceOrganisation
import org.entur.oauth2.JwtRoleAssignmentExtractor
import org.entur.ror.permission.RemoteBabaRoleAssignmentExtractor
import org.rutebanken.helper.organisation.RoleAssignmentExtractor
import org.rutebanken.helper.organisation.authorization.AuthorizationService
import org.rutebanken.helper.organisation.authorization.DefaultAuthorizationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class AuthorizationConfig {

    @Profile("local", "test")
    @ConditionalOnProperty(
        value = ["anzu.security.role-assignment-extractor"],
        havingValue = "jwt",
    )
    @Bean
    fun jwtRoleAssignmentExtractor(): RoleAssignmentExtractor = JwtRoleAssignmentExtractor()

    @ConditionalOnProperty(
        value = ["anzu.security.role-assignment-extractor"],
        havingValue = "baba",
        matchIfMissing = true,
    )
    @Bean
    fun babaRoleAssignmentExtractor(
        webClient: WebClient,
        @Value("\${anzu.security.baba-url}") url: String,
    ): RoleAssignmentExtractor = RemoteBabaRoleAssignmentExtractor(webClient, url)

    /** Keyed on codespace: the service is generic over the key and only needs an organisation code. */
    @ConditionalOnProperty(
        value = ["anzu.security.authorization-service"],
        havingValue = "token-based",
        matchIfMissing = true,
    )
    @Bean("authorizationService")
    fun tokenBasedAuthorizationService(
        roleAssignmentExtractor: RoleAssignmentExtractor,
    ): AuthorizationService<String> =
        DefaultAuthorizationService(
            { codespace -> CodespaceOrganisation.of(codespace) },
            roleAssignmentExtractor,
        )

    /**
     * Local development only. Grants the one check anzu makes; everything else is inherited.
     */
    @Profile("local")
    @ConditionalOnProperty(
        value = ["anzu.security.authorization-service"],
        havingValue = "local",
    )
    @Bean("authorizationService")
    fun localAuthorizationService(
        roleAssignmentExtractor: RoleAssignmentExtractor,
    ): AuthorizationService<String> =
        object : DefaultAuthorizationService<String>(roleAssignmentExtractor) {
            override fun canEditRouteData(codespace: String) = true
        }
}
