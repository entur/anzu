package no.entur.anzu.api

import no.entur.anzu.index.RunStatus
import no.entur.anzu.index.Stage
import no.entur.anzu.index.ValidationRun
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.rutebanken.helper.organisation.authorization.AuthorizationService
import java.time.Instant

class ReportAccessTest {

    private val authorizationService: AuthorizationService<String> = mock()
    private val access = ReportAccess(authorizationService)

    private val t0: Instant = Instant.parse("2026-09-25T10:30:00Z")

    private fun run(codespace: String, stage: Stage, completedAt: Instant?) = ValidationRun(
        validationReportId = "id-${stage.name}",
        correlationId = "c-1",
        codespace = codespace,
        referential = "rb_$codespace",
        stage = stage,
        status = if (completedAt == null) RunStatus.IN_PROGRESS else RunStatus.OK,
        startedAt = t0,
        completedAt = completedAt,
    )

    @Test
    fun `the codespace comes from the earliest run`() {
        // Runs under one correlation id need not share a codespace: an import crossing into a
        // migration target records the target on its later runs.
        val runs = listOf(
            run("rut", Stage.PREVALIDATION, t0.plusSeconds(60)),
            run("sky", Stage.POSTVALIDATION, t0.plusSeconds(600)),
        )
        whenever(authorizationService.canEditRouteData("RUT")).thenReturn(true)

        assertEquals("rut", access.authorize(runs, "c-1"))
    }

    @Test
    fun `authorization uses the codespace and never the referential`() {
        whenever(authorizationService.canEditRouteData("RUT")).thenReturn(true)

        access.authorize(listOf(run("rut", Stage.PREVALIDATION, t0)), "c-1")

        verify(authorizationService).canEditRouteData("RUT")
        verify(authorizationService, never()).canEditRouteData("RB_RUT")
    }

    @Test
    fun `access is refused when the caller holds no matching role assignment`() {
        whenever(authorizationService.canEditRouteData("RUT")).thenReturn(false)

        assertThrows<ForbiddenException> {
            access.authorize(listOf(run("rut", Stage.PREVALIDATION, t0)), "c-1")
        }
    }

    @Test
    fun `the refusal names the correlation id and never the codespace`() {
        // A caller must not learn which codespace a correlation id belongs to by being denied.
        whenever(authorizationService.canEditRouteData("RUT")).thenReturn(false)

        val thrown = assertThrows<ForbiddenException> {
            access.authorize(listOf(run("rut", Stage.PREVALIDATION, t0)), "c-1")
        }

        assertTrue(thrown.message!!.contains("c-1"))
        assertFalse(thrown.message!!.contains("rut", ignoreCase = true))
    }

    @Test
    fun `an unknown codespace is refused, not granted`() {
        whenever(authorizationService.canEditRouteData("NOPE")).thenReturn(false)

        assertThrows<ForbiddenException> {
            access.authorize(listOf(run("nope", Stage.PREVALIDATION, t0)), "c-1")
        }
    }

    @Test
    fun `an in-progress run does not win the earliest-run tiebreak`() {
        val runs = listOf(
            run("rut", Stage.PREVALIDATION, t0.plusSeconds(60)),
            run("sky", Stage.POSTVALIDATION, null),
        )
        whenever(authorizationService.canEditRouteData("RUT")).thenReturn(true)

        assertEquals("rut", access.authorize(runs, "c-1"))
    }
}
