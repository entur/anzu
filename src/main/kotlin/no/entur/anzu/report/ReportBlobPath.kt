package no.entur.anzu.report

import no.entur.anzu.index.ValidationRun

/**
 * Where antu filed a run's report:
 * `reports/{referential}/validation-report-{validationReportId}.json`
 *
 * The folder is the stored referential verbatim — prevalidation reports are filed under the bare
 * codespace, every later stage under the `rb_`-prefixed form. Deriving it from the codespace, or
 * special-casing prevalidation, breaks one of the two.
 */
object ReportBlobPath {

    private const val REPORTS_PREFIX = "reports/"
    private const val REPORT_PREFIX = "/validation-report-"
    private const val REPORT_SUFFIX = ".json"

    fun of(run: ValidationRun): String =
        REPORTS_PREFIX + run.referential + REPORT_PREFIX + run.validationReportId + REPORT_SUFFIX
}
