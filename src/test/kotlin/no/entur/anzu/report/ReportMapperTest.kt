package no.entur.anzu.report

import no.entur.anzu.api.model.Severity
import no.entur.anzu.api.model.ValidationEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.ObjectMapper

@SpringBootTest(classes = [JacksonAutoConfiguration::class])
class ReportMapperTest {

    @Autowired private lateinit var objectMapper: ObjectMapper
    private val mapper by lazy { ReportMapper(objectMapper) }

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/reports/$name")) { "missing fixture $name" }
            .readBytes()

    @Test
    fun `a clean run yields an empty entry list, not null`() {
        val json = """{"codespace":"tst","validationReportId":"tst_1",
            "numberOfValidationEntriesPerRule":{},"validationReportEntries":[]}""".toByteArray()

        assertEquals(emptyList<ValidationEntry>(), mapper.toEntries(json))
    }

    @Test
    fun `location fields are read straight off the entry`() {
        // Antu publishes DataLocation flat. A nested dataLocation object would mean it changed.
        val entry = mapper.toEntries(fixture("validation-report.json"))
            .first { it.lineNumber != null }

        assertTrue(entry.fileName!!.endsWith(".xml"))
        assertTrue(entry.objectId!!.startsWith("TST:"))
    }

    @Test
    fun `placeholder line, column and file name become null`() {
        val json = """
            {
              "codespace": "tst",
              "validationReportId": "tst_1",
              "validationReportEntries": [
                {
                  "message": "something",
                  "name": "SYSTEM_ERROR",
                  "severity": "ERROR",
                  "fileName": "N/A",
                  "objectId": null,
                  "lineNumber": 0,
                  "columnNumber": 0
                }
              ],
              "numberOfValidationEntriesPerRule": { "SYSTEM_ERROR": 1 }
            }
        """.trimIndent().toByteArray()

        val entry = mapper.toEntries(json).single()

        assertNull(entry.fileName, "\"N/A\" is not a file name")
        assertNull(entry.lineNumber, "0 means unknown, not line zero")
        assertNull(entry.columnNumber)
    }

    @Test
    fun `severity is read per entry, so two entries sharing a code may differ`() {
        val json = """
            {
              "codespace": "tst",
              "validationReportId": "tst_1",
              "validationReportEntries": [
                { "message": "a", "name": "RULE_X", "severity": "ERROR", "fileName": "a.xml", "objectId": null, "lineNumber": 1, "columnNumber": 1 },
                { "message": "b", "name": "RULE_X", "severity": "WARNING", "fileName": "b.xml", "objectId": null, "lineNumber": 2, "columnNumber": 1 }
              ],
              "numberOfValidationEntriesPerRule": { "RULE_X": 2 }
            }
        """.trimIndent().toByteArray()

        val entries = mapper.toEntries(json)

        assertEquals(setOf("RULE_X"), entries.map { it.code }.toSet())
        assertEquals(setOf(Severity.ERROR, Severity.WARNING), entries.map { it.severity }.toSet())
    }

    @Test
    fun `entries are ordered by severity, then by file name`() {
        val json = """
            {
              "codespace": "tst",
              "validationReportId": "tst_1",
              "validationReportEntries": [
                { "message": "i", "name": "R", "severity": "INFO", "fileName": "a.xml", "objectId": null, "lineNumber": 1, "columnNumber": 1 },
                { "message": "e2", "name": "R", "severity": "ERROR", "fileName": "z.xml", "objectId": null, "lineNumber": 1, "columnNumber": 1 },
                { "message": "c", "name": "R", "severity": "CRITICAL", "fileName": "m.xml", "objectId": null, "lineNumber": 1, "columnNumber": 1 },
                { "message": "w", "name": "R", "severity": "WARNING", "fileName": "m.xml", "objectId": null, "lineNumber": 1, "columnNumber": 1 },
                { "message": "e1", "name": "R", "severity": "ERROR", "fileName": "a.xml", "objectId": null, "lineNumber": 1, "columnNumber": 1 }
              ],
              "numberOfValidationEntriesPerRule": { "R": 5 }
            }
        """.trimIndent().toByteArray()

        assertEquals(listOf("c", "e1", "e2", "w", "i"), mapper.toEntries(json).map { it.message })
    }

    @Test
    fun `the antu report id is dropped, not relocated`() {
        val entries = mapper.toEntries(fixture("validation-report.json"))

        assertTrue(
            !objectMapper.writeValueAsString(entries).contains("validationReportId"),
            "no antu identifier may appear anywhere in a mapped entry"
        )
    }

    @Test
    fun `an unknown severity becomes UNKNOWN and the entry is kept`() {
        val json = """
            {
              "codespace": "tst",
              "validationReportId": "tst_1",
              "validationReportEntries": [
                { "message": "x", "name": "R", "severity": "SOMETHING_NEW",
                  "fileName": "a.xml", "objectId": null, "lineNumber": 1, "columnNumber": 1 }
              ],
              "numberOfValidationEntriesPerRule": { "R": 1 }
            }
        """.trimIndent().toByteArray()

        val entry = mapper.toEntries(json).single()

        assertEquals(Severity.UNKNOWN, entry.severity)
        assertEquals("R", entry.code)
        assertEquals("x", entry.message)
    }

    @Test
    fun `a missing severity is UNKNOWN rather than a parse failure`() {
        val json = """
            {
              "codespace": "tst",
              "validationReportId": "tst_1",
              "validationReportEntries": [
                { "message": "x", "name": "R", "fileName": "a.xml" }
              ]
            }
        """.trimIndent().toByteArray()

        assertEquals(Severity.UNKNOWN, mapper.toEntries(json).single().severity)
    }

    @Test
    fun `UNKNOWN entries sort last, below INFO`() {
        val json = """
            {
              "codespace": "tst",
              "validationReportId": "tst_1",
              "validationReportEntries": [
                { "message": "u", "name": "R", "severity": "SOMETHING_NEW", "fileName": "a.xml" },
                { "message": "i", "name": "R", "severity": "INFO", "fileName": "z.xml" }
              ]
            }
        """.trimIndent().toByteArray()

        assertEquals(listOf("i", "u"), mapper.toEntries(json).map { it.message })
    }

    @Test
    fun `an unparseable blob raises a mapping exception`() {
        org.junit.jupiter.api.assertThrows<ReportMappingException> {
            mapper.toEntries("not json at all".toByteArray())
        }
    }
}
