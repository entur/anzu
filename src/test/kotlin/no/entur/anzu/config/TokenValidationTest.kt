package no.entur.anzu.config

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import no.entur.anzu.PostgresTestBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.Date

/**
 * Translating the library's refusals must not weaken what it validates: a token naming the trusted
 * issuer is still refused unless its signature, audience and expiry hold.
 *
 * A stub issuer on loopback makes the decoder do real discovery and real JWKS verification.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestBase::class)
@ActiveProfiles("test")
class TokenValidationTest {

    @LocalServerPort private var port: Int = 0

    @Test
    fun `a sound token is authenticated`() {
        // 404 is the empty index answering: authentication passed, then there was no such import.
        // Asserting that rather than "not 401" keeps a 500 from passing for a success.
        assertEquals(404, get(token()).statusCode())
    }

    @Test
    fun `a token signed by the wrong key is refused`() {
        assertEquals(401, get(token(key = unpublishedKey)).statusCode())
    }

    @Test
    fun `an expired token is refused`() {
        assertEquals(401, get(token(expiry = Instant.now().minusSeconds(60))).statusCode())
    }

    @Test
    fun `a token for another audience is refused`() {
        assertEquals(401, get(token(audience = "https://api.example.invalid")).statusCode())
    }

    @Test
    fun `an unsigned token claiming the trusted issuer is refused`() {
        val claims = """{"iss":"$issuer","aud":"$AUDIENCE","sub":"caller"}"""
        val unsigned = listOf("""{"alg":"none"}""", claims, "")
            .joinToString(".") {
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray())
            }

        assertEquals(401, get(unsigned).statusCode())
    }

    private fun get(token: String): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://localhost:$port/validation-reports/c-1"))
                .header("Authorization", "Bearer $token")
                .header("ET-Client-Name", "anzu-test")
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun token(
        key: RSAKey = signingKey,
        audience: String = AUDIENCE,
        expiry: Instant = Instant.now().plusSeconds(300),
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject("caller")
            .expirationTime(Date.from(expiry))
            .build()

        return SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(),
            claims,
        ).apply { sign(RSASSASigner(key)) }.serialize()
    }

    companion object {
        private const val AUDIENCE = "https://api.test.entur.io"

        private val signingKey: RSAKey = RSAKeyGenerator(2048).keyID("anzu-test").generate()

        /** Well formed and correctly signed, by a key the issuer never published. */
        private val unpublishedKey: RSAKey = RSAKeyGenerator(2048).keyID("anzu-test").generate()

        private val server: HttpServer =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/.well-known/jwks.json") { exchange ->
                    respond(exchange, JWKSet(listOf(signingKey.toPublicJWK())).toString())
                }
                createContext("/.well-known/openid-configuration") { exchange ->
                    respond(
                        exchange,
                        """
                        {"issuer":"$issuer",
                         "authorization_endpoint":"$issuer/authorize",
                         "token_endpoint":"$issuer/oauth/token",
                         "jwks_uri":"$issuer/.well-known/jwks.json",
                         "response_types_supported":["code"],
                         "subject_types_supported":["public"],
                         "id_token_signing_alg_values_supported":["RS256"]}
                        """.trimIndent(),
                    )
                }
                start()
            }

        private val issuer: String get() = "http://127.0.0.1:${server.address.port}"

        private fun respond(exchange: com.sun.net.httpserver.HttpExchange, body: String) {
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        @JvmStatic
        @DynamicPropertySource
        fun issuerProperties(registry: DynamicPropertyRegistry) {
            registry.add("anzu.oauth2.resourceserver.partner.jwt.issuer-uri") { issuer }
            registry.add("anzu.oauth2.resourceserver.partner.jwt.audience") { AUDIENCE }
        }

        @JvmStatic
        @AfterAll
        fun stopIssuer() = server.stop(0)
    }
}
