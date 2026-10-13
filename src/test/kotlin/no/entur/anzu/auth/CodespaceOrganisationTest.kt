package no.entur.anzu.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CodespaceOrganisationTest {

    @Test
    fun `an organisation code is the codespace uppercased`() {
        assertEquals("RUT", CodespaceOrganisation.of("rut"))
        assertEquals("SKY", CodespaceOrganisation.of("sky"))
    }

    @Test
    fun `an already uppercase codespace is unchanged`() {
        assertEquals("RUT", CodespaceOrganisation.of("RUT"))
    }

    @Test
    fun `a referential is not silently turned into a codespace`() {
        // No rb_ stripping. A referential passed here by mistake stays distinct and matches nothing.
        assertEquals("RB_RUT", CodespaceOrganisation.of("rb_rut"))
    }
}
