package com.whybuy.app.overlay

enum class OverlayStep {
    GREETING,
    EMOTION,
    DECISION,
    FAREWELL
}

enum class Emotion(val label: String) {
    TIRED("조금 지쳤어요."),
    BROWSING("그냥 구경 중이에요."),
    REWARD("나에게 선물하고 싶어요."),
    NEEDED("정말 필요한 것 같아요."),
    UNKNOWN("잘 모르겠어요.")
}

enum class Decision(val label: String) {
    BUY_NOW("지금 살게요."),
    THINK_MORE("조금 더 볼게요."),
    LATER("다음에 올게요.")
}

enum class EndReason {
    COMPLETED,
    DECLINED,
    DISMISSED_TIMEOUT,
    APP_CLOSED
}