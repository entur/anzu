package no.entur.anzu.config

import com.google.cloud.spring.pubsub.core.PubSubTemplate
import no.entur.anzu.PostgresTestBase
import no.entur.anzu.index.RunStatus
import no.entur.anzu.index.Stage
import no.entur.anzu.index.ValidationEventRepository
import no.entur.anzu.ingest.PubSubAttributes.CORRELATION_ID
import no.entur.anzu.ingest.PubSubAttributes.DATASET_CODESPACE
import no.entur.anzu.ingest.PubSubAttributes.DATASET_REFERENTIAL
import no.entur.anzu.ingest.PubSubAttributes.VALIDATION_REPORT_ID
import no.entur.anzu.ingest.PubSubAttributes.VALIDATION_STAGE
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PubSubEmulatorContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * The transport, once: a message published on the topic reaches the listener and lands in the
 * index. Everything else about ingest is covered without a container.
 *
 * Runs entirely against the emulator — [PubSubEmulatorInitializer] creates the topic and
 * subscription, and no credentials are resolved.
 */
@SpringBootTest(
    properties = [
        "anzu.pubsub.enabled=true",
        "spring.cloud.gcp.core.enabled=true",
        "spring.cloud.gcp.pubsub.enabled=true",
        "spring.cloud.gcp.project-id=anzu-local",
        "anzu.pubsub.validation-status-topic=AntuNetexValidationStatusQueue",
        "anzu.pubsub.validation-status-subscription=anzu-validation-status-subscription",
    ]
)
@Import(PostgresTestBase::class)
// The Pub/Sub admin clients hold non-daemon gax threads that otherwise outlive the test run.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PubSubWiringTest {

    @Autowired private lateinit var pubSubTemplate: PubSubTemplate
    @Autowired private lateinit var repository: ValidationEventRepository

    @Test
    fun `a published status message reaches the index`() {
        pubSubTemplate.publish(
            "AntuNetexValidationStatusQueue",
            "ok",
            mapOf(
                VALIDATION_STAGE to "EnturValidationStagePreValidation",
                VALIDATION_REPORT_ID to "tst_wiring",
                CORRELATION_ID to "c-wiring",
                DATASET_CODESPACE to "tst",
                DATASET_REFERENTIAL to "tst",
            ),
        )

        // assertEquals, not single() — Awaitility only retries AssertionError, so the
        // NoSuchElementException from an empty list would fail the test on the first poll.
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            val runs = repository.findRuns("c-wiring")
            assertEquals(1, runs.size, "no run indexed yet")
            assertEquals(Stage.PREVALIDATION, runs.first().stage)
            assertEquals(RunStatus.OK, runs.first().status)
        }
    }

    companion object {
        private lateinit var emulator: PubSubEmulatorContainer

        @JvmStatic
        @BeforeAll
        fun startEmulator() {
            emulator = PubSubEmulatorContainer(
                DockerImageName.parse("gcr.io/google.com/cloudsdktool/cloud-sdk:emulators")
            )
            emulator.start()
        }

        @JvmStatic
        @AfterAll
        fun stopEmulator() {
            emulator.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun emulatorProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.cloud.gcp.pubsub.emulator-host") { emulator.emulatorEndpoint }
        }
    }
}
