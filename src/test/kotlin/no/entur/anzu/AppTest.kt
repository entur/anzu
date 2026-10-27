package no.entur.anzu

import no.entur.anzu.config.ValidationStatusSubscription
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/** The whole bean graph starts, and Flyway applies the schema. */
@SpringBootTest
@Import(PostgresTestBase::class)
@ActiveProfiles("test")
class AppTest {

    @Autowired private lateinit var context: ApplicationContext

    @Test
    fun `the application context loads`() {
    }

    @Test
    fun `no subscriber exists, so no test reaches Pub-Sub`() {
        assertTrue(context.getBeansOfType(ValidationStatusSubscription::class.java).isEmpty())
    }

    /** The cluster's configuration is loaded here too, not shadowed by a test copy. */
    @Test
    fun `the shipped configuration is in effect`() {
        // Set only in src/main/resources/application.properties, so reading it back proves the
        // shipped file is loaded rather than shadowed by the test one.
        assertEquals(
            "health,prometheus",
            context.environment.getProperty("management.endpoints.web.exposure.include"),
        )
    }
}
