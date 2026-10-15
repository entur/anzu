package no.entur.anzu.config

import no.entur.anzu.api.ProblemDetailBody
import org.entur.oauth2.multiissuer.MultiIssuerAuthenticationManagerResolver
import org.entur.oauth2.multiissuer.MultiIssuerAuthenticationManagerResolverBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain

/** Anzu validates the token itself rather than trusting anything upstream. */
@Configuration
@EnableWebSecurity
class WebSecurityConfig {

    /**
     * With no issuer configured the resolver rejects every token, so the default is deny.
     *
     * The builder's method names come from the helper library and name a provider; the issuer that
     * is actually trusted is whatever the property is set to.
     */
    @Bean
    fun authenticationManagerResolver(
        @Value("\${anzu.oauth2.resourceserver.partner.jwt.issuer-uri:}") issuer: String,
        @Value("\${anzu.oauth2.resourceserver.partner.jwt.audience:}") audience: String,
    ): MultiIssuerAuthenticationManagerResolver =
        MultiIssuerAuthenticationManagerResolverBuilder()
            .withEnturPartnerAuth0Issuer(issuer)
            .withEnturPartnerAuth0Audience(audience)
            .build()

    @Bean
    fun filterChain(
        http: HttpSecurity,
        authenticationManagerResolver: MultiIssuerAuthenticationManagerResolver,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer {
                it.authenticationManagerResolver(authenticationManagerResolver)
                it.authenticationEntryPoint(problemJsonUnauthorized())
            }
            .build()

    /**
     * A 401 is raised by the filter chain, so [no.entur.anzu.api.ProblemDetailsHandler] never sees
     * it. Without this the response is an empty body, and the contract promises problem+json for
     * every error. Same shape as every other problem, serialised here by hand because the advice is
     * out of reach.
     */
    private fun problemJsonUnauthorized() = AuthenticationEntryPoint { _, response, _ ->
        val problem = ProblemDetailBody(
            title = HttpStatus.UNAUTHORIZED.reasonPhrase,
            status = HttpStatus.UNAUTHORIZED.value(),
        )
        response.status = problem.status
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.setHeader("WWW-Authenticate", "Bearer")
        response.writer.write("""{"title":"${problem.title}","status":${problem.status}}""")
    }
}
