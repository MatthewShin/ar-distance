package com.reodavin.ardistance.pipeline

/**
 * 앱 전체의 측정 흐름 상태 (계획 §3, + 1타겟/2타겟 모드 확장).
 *
 * [SelectingMode] → [SelectingBox1] → (2타겟 모드면 [SelectingBox2]) → [Tracking] 순서로 진행한다.
 * [Tracking]의 box1/box2는 [FrameProcessor]가 매 프레임 갱신하는 화면(VIEW) 좌표계 사각형이다.
 * 1타겟 모드에서는 box2/box2Lost가 항상 사용되지 않는다(null/false 고정).
 */
sealed class MeasurementState {
    /** 앱 시작 직후 — 1타겟/2타겟 모드를 선택해야 하는 상태. */
    data object SelectingMode : MeasurementState()

    /** 박스1(1타겟 모드에서는 유일한 박스)을 아직 지정하지 않은 상태. */
    data class SelectingBox1(val mode: MeasurementMode) : MeasurementState()

    /** (2타겟 모드 전용) 박스1은 확정됐고, 박스2를 지정해야 하는 상태. */
    data class SelectingBox2(val mode: MeasurementMode, val box1: PixelRect) : MeasurementState()

    /** 지정이 끝나 실시간 추적 중인 상태. */
    data class Tracking(
        val mode: MeasurementMode,
        val box1: PixelRect?,
        val box2: PixelRect?,
        val box1Lost: Boolean = false,
        val box2Lost: Boolean = false,
        /** 실측 거리(미터). depth 무효 프레임에서는 갱신되지 않고 이전 값을 유지한다 (계획 §6). */
        val distanceMeters: Float? = null,
        /** true면 depth 신뢰도가 낮은 상태에서 계산된 값(장거리/저텍스처 등) — [distanceMeters]와 함께만 갱신된다. */
        val distanceLowConfidence: Boolean = false,
        /**
         * 다중 시점 삼각측량 기반 스냅샷 정밀측정 진행 상태. 별도 모드 선택 없이, 타겟이 추적
         * 중이면 언제든 "정밀 측정" 버튼으로 시작할 수 있다.
         */
        val precisionCapture: PrecisionCaptureState = PrecisionCaptureState.Idle,
    ) : MeasurementState()
}

/** [MeasurementState.Tracking.precisionCapture]의 세부 상태. */
sealed class PrecisionCaptureState {
    /** 아직 정밀측정을 시작하지 않았거나, 직전 결과가 없는 상태. */
    data object Idle : PrecisionCaptureState()

    /** 관측치를 모으는 중. [baselineMeters]가 [targetBaselineMeters]에 도달하면 자동 확정된다. */
    data class Capturing(val baselineMeters: Float, val targetBaselineMeters: Float) : PrecisionCaptureState()

    /** 삼각측량 성공 — 재측정 전까지 이 값을 고정 표시한다. */
    data class Result(val distanceMeters: Float) : PrecisionCaptureState()

    /** 삼각측량 실패(타겟 유실, 광선이 거의 평행 등). */
    data class Failed(val reason: String) : PrecisionCaptureState()
}
