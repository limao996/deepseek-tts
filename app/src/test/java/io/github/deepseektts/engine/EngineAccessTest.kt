package io.github.deepseektts.engine

import org.junit.Assert.*
import org.junit.Test

class EngineAccessTest {
    @Test fun ownProcessIsAlwaysAllowed() {
        assertNull(EngineAccess.denyReason(10_193, 10_193, allowThirdPartyRequests = false))
    }

    @Test fun otherAppsAreBlockedByDefault() {
        assertEquals(EngineAccess.THIRD_PARTY_DISABLED, EngineAccess.denyReason(10_200, 10_193, allowThirdPartyRequests = false))
    }

    @Test fun otherAppsPassOnlyAfterExplicitConsent() {
        assertNull(EngineAccess.denyReason(10_200, 10_193, allowThirdPartyRequests = true))
    }
}
