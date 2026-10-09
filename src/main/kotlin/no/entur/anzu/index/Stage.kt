package no.entur.anzu.index

/**
 * The curated external stage vocabulary. Antu's internal stage constants map onto this so partner
 * contracts do not freeze Entur's internal step names.
 *
 * [segment] is the kebab-case URL form; the enum name is the upper-snake form used in response
 * bodies.
 */
enum class Stage(val segment: String) {
    PREVALIDATION("prevalidation"),
    POSTVALIDATION("postvalidation"),
    BLOCKS_POSTVALIDATION("blocks-postvalidation"),
    FLEX_POSTVALIDATION("flex-postvalidation"),
    MERGED_POSTVALIDATION("merged-postvalidation");

    companion object {
        private val BY_SEGMENT = entries.associateBy { it.segment }

        /** Null for a segment that is not a stage at all — the caller answers 404, not 400. */
        fun fromSegment(segment: String): Stage? = BY_SEGMENT[segment]
    }
}
