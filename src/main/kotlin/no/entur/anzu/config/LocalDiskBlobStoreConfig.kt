package no.entur.anzu.config

import org.rutebanken.helper.storage.repository.BlobStoreRepository
import org.rutebanken.helper.storage.repository.LocalDiskBlobStoreRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** Reads reports antu wrote locally, under `<local-path>/<report-container>/reports/`. */
@Configuration
@Profile("local")
class LocalDiskBlobStoreConfig {

    @Bean
    fun reportBlobStoreRepository(
        @Value("\${anzu.blobstore.local-path}") baseFolder: String,
        @Value("\${anzu.blobstore.report-container}") container: String,
    ): BlobStoreRepository =
        LocalDiskBlobStoreRepository(baseFolder).apply { setContainerName(container) }
}
