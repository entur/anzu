package no.entur.anzu

import no.entur.anzu.config.ValidationStatusSubscription
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import

/** The whole bean graph starts, and Flyway applies the schema. */
@SpringBootTest
@Import(PostgresTestBase::class)
class AppTest {

    @Autowired private lateinit var context: ApplicationContext

    @Test
    fun `the application context loads`() {
    }

    @Test
    fun `no subscriber exists, so no test reaches Pub-Sub`() {
        assertTrue(context.getBeansOfType(ValidationStatusSubscription::class.java).isEmpty())
    }
}
