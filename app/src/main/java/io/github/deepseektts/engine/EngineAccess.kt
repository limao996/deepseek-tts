package io.github.deepseektts.engine

/** Pure policy so the billing boundary is testable without a second installed app. */
object EngineAccess {
    const val THIRD_PARTY_DISABLED = "第三方系统引擎默认关闭。请在鲸语 TTS 设置中阅读费用与隐私说明并明确启用。"

    /** Null means allowed. The owning app can always preview; other UIDs need explicit consent. */
    fun denyReason(callerUid: Int, ownUid: Int, allowThirdPartyRequests: Boolean): String? =
        if (callerUid != ownUid && !allowThirdPartyRequests) THIRD_PARTY_DISABLED else null
}
