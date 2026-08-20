package com.reodavin.ardistance.pipeline

/** [MeasurementMode](타겟 개수)와 직교하는 축 — 거리 계산 방식을 고른다. */
enum class AccuracyMode {
    /** ARCore depth 센서(신뢰도 필터링 포함) 기반, 매 프레임 실시간 계산. */
    NORMAL,

    /** 다중 시점 삼각측량 기반 스냅샷 정밀측정 — [FrameProcessor.startPrecisionCapture] 참고. */
    HIGH_ACCURACY,
}
