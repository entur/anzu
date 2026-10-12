package no.entur.anzu.config

import com.google.cloud.NoCredentials
import com.google.cloud.storage.StorageOptions
import org.rutebanken.helper.gcp.repository.GcsBlobStoreRepository
import org.rutebanken.helper.storage.repository.BlobStoreRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("gcp")
class GcsBlobStoreConfig {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun reportBlobStoreRepository(
        @Value("\${anzu.gcp.project-id}") projectId: String,
        @Value("\${anzu.blobstore.report-container}") container: String,
        @Value("\${blobstore.gcs.credential.path:#{null}}") credentialPath: String?,
    ): BlobStoreRepository {
        val emulatorHost = System.getenv("STORAGE_EMULATOR_HOST")
        val repository = if (!emulatorHost.isNullOrBlank()) {
            logger.info("Using the GCS emulator at {}", emulatorHost)
            GcsBlobStoreRepository(
                StorageOptions.newBuilder()
                    .setHost(emulatorHost)
                    .setProjectId(projectId)
                    .setCredentials(NoCredentials.getInstance())
                    .build()
                    .service
            )
        } else {
            GcsBlobStoreRepository(projectId, credentialPath)
        }
        repository.setContainerName(container)
        return repository
    }
}
