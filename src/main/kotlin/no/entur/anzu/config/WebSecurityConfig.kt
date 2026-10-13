package no.entur.anzu.config

import org.entur.oauth2.multiissuer.MultiIssuerAuthenticationManagerResolver
import org.entur.oauth2.multiissuer.MultiIssuerAuthenticationManagerResolverBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/** Anzu validates the token itself rather than trusting anything upstream. */
@Configuration
@EnableWebSecurity
class WebSecurityConfig {

    /** With no issuer configured the resolver rejects every token, so the default is deny. */
    @Bean
    fun authenticationManagerResolver(
        @Value("\${anzu.oauth2.resourceserver.auth0.partner.jwt.issuer-uri:}") issuer: String,
        @Value("\${anzu.oauth2.resourceserver.auth0.partner.jwt.audience:}") audience: String,
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
            }
            .build()
}
