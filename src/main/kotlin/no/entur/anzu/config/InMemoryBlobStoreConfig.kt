package no.entur.anzu.config

import org.rutebanken.helper.storage.repository.BlobStoreRepository
import org.rutebanken.helper.storage.repository.InMemoryBlobStoreRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("test")
class InMemoryBlobStoreConfig {

    @Bean
    fun reportBlobStoreRepository(
        @Value("\${anzu.blobstore.report-container}") container: String,
    ): BlobStoreRepository =
        InMemoryBlobStoreRepository(mutableMapOf()).apply { setContainerName(container) }
}
