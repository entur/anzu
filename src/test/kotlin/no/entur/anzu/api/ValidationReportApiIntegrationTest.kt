package no.entur.anzu.api

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import no.entur.anzu.PostgresTestBase
import no.entur.anzu.index.RunStatus
import no.entur.anzu.index.Stage
import no.entur.anzu.index.ValidationEvent
import no.entur.anzu.index.ValidationEventRepository
import no.entur.anzu.index.ValidationRun
import no.entur.anzu.report.BlobResult
import no.entur.anzu.report.ReportBlobPath
import no.entur.anzu.report.ReportBlobStore
import org.entur.oauth2.multiissuer.MultiIssuerAuthenticationManagerResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.rutebanken.helper.organisation.authorization.AuthorizationService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

/**
 * The real stack over real HTTP: Postgres, the security filter chain, the controller and the
 * serialised JSON.
 *
 * Three boundaries are mocked, and only those: the token decoder, baba (the permission lookup)
 * and the blob store. Nothing in this test reaches an external service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestBase::class)
class ValidationReportApiIntegrationTest {

    @LocalServerPort private var port: Int = 0

    @Autowired private lateinit var repository: ValidationEventRepository
    @Autowired private lateinit var jdbc: JdbcClient

    @MockitoBean private lateinit var blobs: ReportBlobStore

    @MockitoBean(name = "authorizationService")
    private lateinit var authorizationService: AuthorizationService<String>

    @MockitoBean private lateinit var authenticationManagerResolver: MultiIssuerAuthenticationManagerResolver

    private val t0: Instant = Instant.parse("2026-09-25T10:30:00Z")

    private val cleanBlob = """
        {"codespace":"tst","validationReportId":"secret-report-id-42",
         "validationReportEntries":[],"numberOfValidationEntriesPerRule":{}}
    """.trimIndent().toByteArray()

    private val findingsBlob = """
        {"codespace":"tst","validationReportId":"secret-report-id-42",
         "validationReportEntries":[
           {"message":"Invalid id structure","name":"NETEX_ID_1","severity":"ERROR",
            "fileName":"TST_line_1.xml","objectId":"TST:ServiceJourney:1",
            "lineNumber":1420,"columnNumber":18}],
         "numberOfValidationEntriesPerRule":{"NETEX_ID_1":1}}
    """.trimIndent().toByteArray()

    @BeforeEach
    fun setUp() {
        jdbc.sql("delete from validation_event").update()

        // Mocks authentication, so no token is verified and no issuer is contacted.
        val authenticated = AuthenticationManager {
            TestingAuthenticationToken("caller", "n/a", "ROLE_USER").apply { isAuthenticated = true }
        }
        whenever(authenticationManagerResolver.resolve(any())).thenReturn(authenticated)

        whenever(authorizationService.canEditRouteData("TST")).thenReturn(true)
    }

    private fun get(path: String): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .header("Authorization", "Bearer test-token")
                .header("ET-Client-Name", "anzu-test")
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun contentType(response: HttpResponse<String>) =
        response.headers().firstValue("content-type").orElse("(none)")

    @Test
    fun `an unknown correlation id is 404 problem+json`() {
        val response = get("/validation-reports/nope")

        assertEquals(404, response.statusCode())
        assertTrue(contentType(response).startsWith("application/problem+json"))
        assertTrue(response.body().contains("\"status\":404"))
        assertTrue(response.body().contains("\"title\""))
    }

    @Test
    fun `a missing ET-Client-Name is 400 problem+json`() {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://localhost:$port/validation-reports/c-1"))
                .header("Authorization", "Bearer test-token")
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(400, response.statusCode())
        assertTrue(contentType(response).startsWith("application/problem+json"))
    }

    @Test
    fun `a caller without rights is 403 and the body never names the codespace`() {
        insert("tst_1", Stage.PREVALIDATION, RunStatus.OK, "tst", t0.plusSeconds(60))
        whenever(authorizationService.canEditRouteData("TST")).thenReturn(false)

        val response = get("/validation-reports/c-1")

        assertEquals(403, response.statusCode())
        assertTrue(response.body().contains("c-1"))
        assertFalse(response.body().lowercase().contains("tst"))
    }

    @Test
    fun `an unauthorized caller gets 403 on every stage segment, including ones that did not run`() {
        // 404 versus 403 must disclose nothing about which steps of someone else's import ran.
        insert("tst_1", Stage.PREVALIDATION, RunStatus.OK, "tst", t0.plusSeconds(60))
        whenever(authorizationService.canEditRouteData("TST")).thenReturn(false)

        listOf("prevalidation", "postvalidation", "merged-postvalidation", "not-a-stage")
            .forEach { segment ->
                assertEquals(403, get("/validation-reports/c-1/$segment").statusCode(), segment)
            }
    }

    @Test
    fun `no validation report id appears anywhere in either body`() {
        // Asserted on the serialised text, so a later convenience field cannot reintroduce the leak.
        insert("secret-report-id-42", Stage.PREVALIDATION, RunStatus.OK, "tst", t0.plusSeconds(60))
        whenever(blobs.read(any())).thenReturn(BlobResult.Found(findingsBlob))

        listOf(get("/validation-reports/c-1"), get("/validation-reports/c-1/prevalidation"))
            .forEach { response ->
                assertEquals(200, response.statusCode())
                assertFalse(
                    response.body().contains("secret-report-id-42"),
                    "an antu identifier leaked into ${response.body()}",
                )
                assertFalse(response.body().contains("validationReportId"))
            }
    }

    @Test
    fun `a terminal run whose blob is absent is 410 on the single resource and null entries on the collection`() {
        insert("tst_1", Stage.PREVALIDATION, RunStatus.FAILED, "tst", t0.plusSeconds(60))
        whenever(blobs.read(any())).thenReturn(BlobResult.Absent)

        val single = get("/validation-reports/c-1/prevalidation")
        val collection = get("/validation-reports/c-1")

        assertEquals(410, single.statusCode())
        assertEquals(200, collection.statusCode())
        assertTrue(collection.body().contains("\"entries\":null"))
    }

    @Test
    fun `an in-progress run is 200 with no entries, not 410`() {
        insert("tst_1", Stage.PREVALIDATION, RunStatus.IN_PROGRESS, "tst", null)

        val response = get("/validation-reports/c-1/prevalidation")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("IN_PROGRESS"))
    }

    @Test
    fun `a read failure is 502, not 410`() {
        insert("tst_1", Stage.PREVALIDATION, RunStatus.OK, "tst", t0.plusSeconds(60))
        whenever(blobs.read(any())).thenReturn(BlobResult.Failed(RuntimeException("503")))

        assertEquals(502, get("/validation-reports/c-1/prevalidation").statusCode())
        assertEquals(502, get("/validation-reports/c-1").statusCode())
    }

    @Test
    fun `an import whose runs disagree on the codespace is refused with 500`() {
        insert("tst_1", Stage.PREVALIDATION, RunStatus.OK, "tst", t0.plusSeconds(60))
        insert("rb_other_2", Stage.POSTVALIDATION, RunStatus.OK, "other", t0.plusSeconds(600))
        whenever(blobs.read(any())).thenReturn(BlobResult.Found(cleanBlob))

        val handlerLog = LoggerFactory.getLogger(ProblemDetailsHandler::class.java) as Logger
        val logged = ListAppender<ILoggingEvent>().apply { start() }
        handlerLog.addAppender(logged)

        try {
            listOf(get("/validation-reports/c-1"), get("/validation-reports/c-1/prevalidation"))
                .forEach { response ->
                    assertEquals(500, response.statusCode())
                    assertTrue(contentType(response).startsWith("application/problem+json"))
                    assertFalse(response.body().lowercase().contains("tst"))
                    assertFalse(response.body().lowercase().contains("other"))
                }

            verify(authorizationService, never()).canEditRouteData(any())

            val refusals = logged.list.filter { it.level == Level.ERROR }
            assertEquals(2, refusals.size, "both endpoints should log the refusal")
            refusals.forEach {
                assertTrue(it.formattedMessage.contains("c-1"), it.formattedMessage)
                assertTrue(it.formattedMessage.contains("tst"), it.formattedMessage)
                assertTrue(it.formattedMessage.contains("other"), it.formattedMessage)
            }
        } finally {
            handlerLog.detachAppender(logged)
        }
    }

    @Test
    fun `both referential forms of one import are one codespace, and are served`() {
        // The case the guard above must not fire on: prevalidation files under the bare codespace
        // and later stages under rb_, and antu maps both back to the same codespace.
        insert("tst_1", Stage.PREVALIDATION, RunStatus.OK, "tst", t0.plusSeconds(60), referential = "tst")
        insert("rb_tst_2", Stage.POSTVALIDATION, RunStatus.OK, "tst", t0.plusSeconds(600), referential = "rb_tst")
        whenever(blobs.read(any())).thenReturn(BlobResult.Found(cleanBlob))

        val response = get("/validation-reports/c-1")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"codespace\":\"tst\""))
    }

    @Test
    fun `both prefix forms resolve from one import`() {
        // An implementation that prepends rb_ unconditionally passes every other test but this one.
        insert("tst_1", Stage.PREVALIDATION, RunStatus.OK, "tst", t0.plusSeconds(60), referential = "tst")
        insert("rb_tst_2", Stage.POSTVALIDATION, RunStatus.OK, "tst", t0.plusSeconds(600), referential = "rb_tst")
        whenever(blobs.read(any())).thenReturn(BlobResult.Found(cleanBlob))

        get("/validation-reports/c-1")

        val runs = argumentCaptor<ValidationRun>()
        verify(blobs, times(2)).read(runs.capture())
        assertEquals(
            setOf(
                "reports/tst/validation-report-tst_1.json",
                "reports/rb_tst/validation-report-rb_tst_2.json",
            ),
            runs.allValues.map { ReportBlobPath.of(it) }.toSet(),
        )
    }

    @Test
    fun `an unanticipated exception still leaves as problem+json`() {
        insert("tst_1", Stage.PREVALIDATION, RunStatus.OK, "tst", t0.plusSeconds(60))
        whenever(blobs.read(any())).thenThrow(IllegalStateException("deep failure naming codespace rut"))

        val response = get("/validation-reports/c-1")

        assertEquals(500, response.statusCode())
        assertTrue(contentType(response).startsWith("application/problem+json"))
        assertFalse(response.body().contains("rut"))
    }

    /** Seeds through the repository, so the test exercises the same path ingest uses. */
    private fun insert(
        reportId: String,
        stage: Stage,
        status: RunStatus,
        codespace: String,
        completedAt: Instant?,
        referential: String = "rb_$codespace",
        correlationId: String = "c-1",
    ) {
        fun event(eventStatus: RunStatus, publishedAt: Instant) = ValidationEvent(
            validationReportId = reportId,
            correlationId = correlationId,
            codespace = codespace,
            referential = referential,
            stage = stage,
            status = eventStatus,
            publishedAt = publishedAt,
        )

        repository.append(event(RunStatus.IN_PROGRESS, t0))
        if (completedAt != null) repository.append(event(status, completedAt))
    }
}
