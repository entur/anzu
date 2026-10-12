package no.entur.anzu.report

import no.entur.anzu.api.model.Severity
import no.entur.anzu.api.model.ValidationEntry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

class ReportMappingException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Maps a published report blob onto our own entry type.
 *
 * Two things change on the way out: antu's report id is stripped, and placeholder values — `"N/A"`
 * for a file name, `0` for a line or column — become null.
 */
@Component
class ReportMapper(private val objectMapper: ObjectMapper) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun toEntries(blob: ByteArray): List<ValidationEntry> {
        val report = try {
            objectMapper.readValue(blob, AntuReportJson::class.java)
        } catch (e: Exception) {
            throw ReportMappingException("Could not parse a validation report blob", e)
        }

        return report.validationReportEntries
            .map(::toEntry)
            .sortedWith(BY_SEVERITY_THEN_FILE)
    }

    private fun toEntry(entry: AntuEntryJson): ValidationEntry =
        ValidationEntry(
            code = entry.name
                ?: throw ReportMappingException("A report entry carries no rule name"),
            severity = severity(entry.severity),
            message = entry.message.orEmpty(),
            fileName = entry.fileName?.takeUnless { it == UNKNOWN_FILE_NAME },
            objectId = entry.objectId,
            lineNumber = entry.lineNumber?.takeUnless { it == 0 },
            columnNumber = entry.columnNumber?.takeUnless { it == 0 },
        )

    /** An unrecognised value is reported as UNKNOWN rather than guessed at or dropped. */
    private fun severity(value: String?): Severity =
        Severity.entries.find { it.name == value }
            ?: Severity.UNKNOWN.also {
                logger.warn("Unmapped severity '{}', reported as UNKNOWN", value)
            }

    companion object {
        private const val UNKNOWN_FILE_NAME = "N/A"

        private val BY_SEVERITY_THEN_FILE =
            compareBy<ValidationEntry> { it.severity.ordinal }
                .thenBy(nullsLast()) { it.fileName }
    }
}
