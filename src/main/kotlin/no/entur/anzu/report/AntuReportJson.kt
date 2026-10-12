package no.entur.anzu.report

/**
 * The published report as antu writes it, for parsing only. Never returned to a caller.
 */
data class AntuReportJson(
    val codespace: String? = null,
    val validationReportEntries: List<AntuEntryJson> = emptyList(),
)

/** Flat: antu serialises the data location onto the entry rather than as a nested object. */
data class AntuEntryJson(
    val message: String? = null,
    val name: String? = null,
    val severity: String? = null,
    val fileName: String? = null,
    val objectId: String? = null,
    val lineNumber: Int? = null,
    val columnNumber: Int? = null,
)
