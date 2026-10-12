package no.entur.anzu.api.model

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Pins the wire shape the contract declares.
 *
 * Loads Spring Boot's Jackson auto-configuration rather than hand-building an ObjectMapper, so these
 * assertions hold against the mapper the application actually serialises with.
 */
@SpringBootTest(classes = [JacksonAutoConfiguration::class])
class ResponseSerialisationTest {

    @Autowired private lateinit var objectMapper: ObjectMapper

    private val t0: OffsetDateTime = OffsetDateTime.of(2026, 9, 25, 10, 30, 0, 0, ZoneOffset.UTC)

    private fun run(entries: List<ValidationEntry>?) = ValidationRunSummary(
        stage = "PREVALIDATION",
        status = RunStatusResponse.OK,
        startedAt = t0,
        completedAt = t0.plusMinutes(4),
        entries = entries,
    )

    private fun tree(value: Any): ObjectNode =
        objectMapper.readTree(objectMapper.writeValueAsString(value)) as ObjectNode

    @Test
    fun `codespace appears once, on the envelope and not on each run`() {
        val json = tree(ValidationReportsResponse("c-1", "tst", listOf(run(emptyList()))))

        assertTrue(json.has("codespace"))
        assertFalse(json["validationRuns"][0].has("codespace"))
    }

    @Test
    fun `an unavailable report serialises entries as an explicit null, not an absent field`() {
        // null means the report is unavailable. A consumer must be able to tell that from a run
        // that validated clean, so the field has to be present.
        val json = tree(run(null))

        assertTrue(json.has("entries"), "entries must be present even when null")
        assertTrue(json["entries"].isNull)
    }

    @Test
    fun `a clean run serialises entries as an empty array`() {
        val json = tree(run(emptyList()))

        assertTrue(json["entries"].isArray)
        assertEquals(0, json["entries"].size())
    }

    @Test
    fun `location fields are flat on the entry, with no nested location object`() {
        val json = tree(ValidationEntry("NETEX_ID_1", Severity.ERROR, "msg", "a.xml", "TST:X:1", 10, 2))

        assertTrue(json.has("fileName") && json.has("objectId"))
        assertTrue(json.has("lineNumber") && json.has("columnNumber"))
        assertFalse(json.has("dataLocation"))
    }

    @Test
    fun `an unknown location serialises as null rather than a sentinel`() {
        val json = tree(ValidationEntry("SYSTEM_ERROR", Severity.ERROR, "msg", null, null, null, null))

        assertTrue(json["fileName"].isNull)
        assertTrue(json["lineNumber"].isNull)
    }

    @Test
    fun `severity is exactly the values the contract declares, in severity order`() {
        // The order is the sort order used for entries, so it is part of the contract.
        assertEquals(
            listOf("CRITICAL", "ERROR", "WARNING", "INFO", "UNKNOWN"),
            Severity.entries.map { it.name }
        )
    }

    @Test
    fun `stage is a string, so a sixth stage is not a breaking change`() {
        assertEquals("SOMETHING_NEW", tree(run(emptyList()).copy(stage = "SOMETHING_NEW"))["stage"].asText())
    }

    @Test
    fun `timestamps are ISO-8601 instants, not epoch numbers`() {
        assertEquals("2026-09-25T10:30:00Z", tree(run(emptyList()))["startedAt"].asText())
    }

    @Test
    fun `no antu identifier can appear on a response type`() {
        val fields = (
            ValidationRunDetail::class.java.declaredFields +
                ValidationRunSummary::class.java.declaredFields +
                ValidationEntry::class.java.declaredFields
            ).map { it.name }

        assertTrue(fields.none { it.contains("validationReportId", ignoreCase = true) })
    }
}
