package app.obsidianmd.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessChecklistTest {
    @Test fun checking_has_no_checklist() {
        assertNull(accessChecklist(ValidationState.Checking))
    }

    @Test fun read_and_write_ok() {
        val c = accessChecklist(ValidationState.Ok(canWrite = true))!!
        assertEquals(CheckStatus.Passed, c.read)
        assertEquals(CheckStatus.Passed, c.write)
        assertTrue(c.canContinue)
        assertFalse(c.readOnlyWarning)
        assertFalse(c.showRetry)
    }

    @Test fun read_ok_write_failed_warns_but_continues() {
        val c = accessChecklist(ValidationState.Ok(canWrite = false))!!
        assertEquals(CheckStatus.Passed, c.read)
        assertEquals(CheckStatus.Failed, c.write)
        assertTrue(c.canContinue)
        assertTrue(c.readOnlyWarning)
    }

    @Test fun read_denied_blocks_continue() {
        val c = accessChecklist(ValidationState.Denied("no access"))!!
        assertEquals(CheckStatus.Failed, c.read)
        assertEquals(CheckStatus.Pending, c.write)   // запись не проверялась — чтения нет
        assertFalse(c.canContinue)
        assertTrue(c.showRetry)
    }

    @Test fun unknown_is_pending_but_allows_continue() {
        val c = accessChecklist(ValidationState.Unknown("no net"))!!
        assertEquals(CheckStatus.Pending, c.read)
        assertEquals(CheckStatus.Pending, c.write)
        assertTrue(c.canContinue)                    // не смогли проверить — не блокируем жёстко
        assertTrue(c.showRetry)
    }
}
