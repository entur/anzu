package no.entur.anzu.auth

/** The organisation code for a codespace: the codespace uppercased. */
object CodespaceOrganisation {

    fun of(codespace: String): String = codespace.uppercase()
}
