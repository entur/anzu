package no.entur.anzu

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Validation reports API.
 *
 * Two halves sharing one table and nothing else: an ingest half that subscribes to antu's validation
 * status queue and maintains an index keyed by correlation id, and a query half that serves the
 * reports themselves by reading antu's bucket directly.
 */
@SpringBootApplication
class App

fun main(args: Array<String>) {
    runApplication<App>(*args)
}
