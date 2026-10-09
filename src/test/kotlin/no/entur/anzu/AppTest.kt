package no.entur.anzu

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/** The whole bean graph starts, and Flyway applies the schema. */
@SpringBootTest
@Import(PostgresTestBase::class)
class AppTest {

    @Test
    fun `the application context loads`() {
    }
}
