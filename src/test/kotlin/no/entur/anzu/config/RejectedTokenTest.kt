package no.entur.anzu.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import no.entur.anzu.PostgresTestBase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

/**
 * A token anzu cannot authenticate answers 401 in the contract's media type and leaves nothing at
 * ERROR. The test profile configures no issuer, so none of these reach a network.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestBase::class)
@ActiveProfiles("test")
class RejectedTokenTest {

    @LocalServerPort private var port: Int = 0

    private val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    private val recorded = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun captureLogging() {
        recorded.start()
        rootLogger.addAppender(recorded)
    }

    @AfterEach
    fun releaseLogging() {
        rootLogger.detachAppender(recorded)
        recorded.stop()
    }

    @Test
    fun `a token that is not a jwt at all is refused without an error`() {
        assertRefusedQuietly("not-a-jwt")
    }

    @Test
    fun `a token missing the signature segment is refused without an error`() {
        assertRefusedQuietly("${segment("""{"alg":"RS256"}""")}.${segment("""{"iss":"x"}""")}")
    }

    @Test
    fun `a token whose payload is not json is refused without an error`() {
        assertRefusedQuietly(
            "${segment("""{"alg":"RS256"}""")}.${segment("not json")}.${segment("signature")}"
        )
    }

    @Test
    fun `a token from an untrusted issuer is refused without an error`() {
        assertRefusedQuietly(jwt("""{"iss":"https://partner.dev.entur.org/","sub":"someone"}"""))
    }

    @Test
    fun `a token carrying no issuer claim is refused without an error`() {
        assertRefusedQuietly(jwt("""{"sub":"someone"}"""))
    }

    /**
     * A refused token must be indistinguishable from no token at all. Compared against a live
     * no-token response rather than a literal, so the two refusal paths cannot drift apart.
     */
    private fun assertRefusedQuietly(token: String) {
        val refused = refuse("Bearer $token")
        val baseline = refuse(null)

        assertEquals(401, refused.statusCode())
        assertEquals(baseline.statusCode(), refused.statusCode())
        assertEquals(baseline.body(), refused.body(), "the problem body differed")
        assertTrue(refused.body().contains("\"status\":401"), "body was '${refused.body()}'")
        for (header in listOf("content-type", "www-authenticate")) {
            assertEquals(
                baseline.headers().firstValue(header).orElse("(none)"),
                refused.headers().firstValue(header).orElse("(none)"),
                "'$header' differed",
            )
        }

        val errors = recorded.list.filter { it.level.isGreaterOrEqual(Level.ERROR) }
        assertTrue(
            errors.isEmpty(),
            "a refused token was logged at ERROR: ${errors.map { it.formattedMessage }}",
        )
    }

    private fun refuse(authorization: String?): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://localhost:$port/validation-reports/c-1"))
                .apply { authorization?.let { header("Authorization", it) } }
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun jwt(claims: String) =
        "${segment("""{"alg":"RS256","typ":"JWT"}""")}.${segment(claims)}.${segment("signature")}"

    private fun segment(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
}
