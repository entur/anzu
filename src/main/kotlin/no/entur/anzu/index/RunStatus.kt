package no.entur.anzu.index

enum class RunStatus {
    IN_PROGRESS,
    OK,
    FAILED;

    val isTerminal: Boolean get() = this != IN_PROGRESS
}
