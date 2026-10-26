package no.entur.anzu.config

import no.entur.anzu.PostgresTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** The filter chain refuses before any handler runs, and still answers in the contract's media type. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestBase::class)
@ActiveProfiles("test")
class UnauthenticatedRequestTest {

    @LocalServerPort private var port: Int = 0

    @Test
    fun `a request with no token is refused with problem+json`() {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://localhost:$port/validation-reports/c-1")).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        val contentType = response.headers().firstValue("content-type").orElse("(none)")

        assertEquals(401, response.statusCode())
        assertTrue(
            contentType.startsWith("application/problem+json"),
            "content type was '$contentType'",
        )
        assertTrue(contentType.contains("UTF-8", ignoreCase = true), "must not be ISO-8859-1")
        assertTrue(response.body().contains("\"status\":401"))
    }

    /**
     * The chart annotates the pod for scraping, so this must answer without a token. It exists only
     * because a registry is on the classpath and the exposure list names it; otherwise the scrape
     * would 404 for the pod's whole life.
     */
    @Test
    fun `the prometheus endpoint is scrapable without a token`() {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://localhost:$port/actuator/prometheus")).build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(200, response.statusCode())
    }
}
