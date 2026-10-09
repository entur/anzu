package no.entur.anzu

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/** The bean graph is sound without Postgres or Pub/Sub. */
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=false",
        "spring.cloud.gcp.pubsub.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    ]
)
class AppTest {

    @Test
    fun `the application context loads`() {
    }
}
