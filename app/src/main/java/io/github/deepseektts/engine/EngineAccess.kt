package io.github.deepseektts.engine

/** Pure policy so the billing boundary is testable without a second installed app. */
object EngineAccess {
    const val THIRD_PARTY_DISABLED = "第三方系统引擎调用已在设置中关闭。如需允许其他应用朗读，请在鲸语 TTS 设置中重新开启。"

    /** Null means allowed. The owning app can always preview; other UIDs need explicit consent. */
    fun denyReason(callerUid: Int, ownUid: Int, allowThirdPartyRequests: Boolean): String? =
        if (callerUid != ownUid && !allowThirdPartyRequests) THIRD_PARTY_DISABLED else null
}
