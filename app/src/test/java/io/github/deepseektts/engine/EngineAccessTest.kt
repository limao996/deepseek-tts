package io.github.deepseektts.engine

import org.junit.Assert.*
import org.junit.Test

class EngineAccessTest {
    @Test fun ownProcessIsAlwaysAllowed() {
        assertNull(EngineAccess.denyReason(10_193, 10_193, allowThirdPartyRequests = false))
    }

    @Test fun otherAppsCanCallByDefault() {
        assertNull(EngineAccess.denyReason(10_200, 10_193, allowThirdPartyRequests = io.github.deepseektts.data.EngineSettings().allowThirdPartyRequests))
    }

    @Test fun otherAppsAreBlockedWhenDisabled() {
        assertEquals(EngineAccess.THIRD_PARTY_DISABLED, EngineAccess.denyReason(10_200, 10_193, allowThirdPartyRequests = false))
    }

    @Test fun otherAppsPassOnlyAfterExplicitConsent() {
        assertNull(EngineAccess.denyReason(10_200, 10_193, allowThirdPartyRequests = true))
    }
}
