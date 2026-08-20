package com.reodavin.ardistance.ui

import androidx.lifecycle.ViewModel
import com.reodavin.ardistance.pipeline.FrameResult
import com.reodavin.ardistance.pipeline.MeasurementMode
import com.reodavin.ardistance.pipeline.MeasurementState
import com.reodavin.ardistance.pipeline.PixelRect
import com.reodavin.ardistance.pipeline.PrecisionCaptureState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 박스 선택/추적 UI 상태를 보유한다. 실제 추적 계산은
 * [com.reodavin.ardistance.pipeline.FrameProcessor]가 수행하고, 그 결과를 [updateFrameResult]로 반영한다.
 */
class MeasurementViewModel : ViewModel() {

    private val _state = MutableStateFlow<MeasurementState>(MeasurementState.SelectingMode)
    val state: StateFlow<MeasurementState> = _state

    fun selectMode(mode: MeasurementMode) {
        _state.value = MeasurementState.SelectingBox1(mode)
    }

    /**
     * 박스를 확정한다. 상태가 바뀌어 새로 추적을 시작해야 하면 해당 박스 인덱스(0 또는 1)를
     * 반환하고, 무시됐으면 null을 반환한다.
     */
    fun confirmBox(rect: PixelRect): Int? {
        return when (val current = _state.value) {
            is MeasurementState.SelectingBox1 -> {
                _state.value = if (current.mode == MeasurementMode.ONE_TARGET) {
                    MeasurementState.Tracking(mode = current.mode, box1 = rect, box2 = null)
                } else {
                    MeasurementState.SelectingBox2(mode = current.mode, box1 = rect)
                }
                0
            }
            is MeasurementState.SelectingBox2 -> {
                _state.value = MeasurementState.Tracking(mode = current.mode, box1 = current.box1, box2 = rect)
                1
            }
            is MeasurementState.SelectingMode, is MeasurementState.Tracking -> null
        }
    }

    /**
     * [FrameProcessor]가 프레임마다 만든 결과를 반영한다. box/거리 모두 이번 프레임에 값이
     * 없으면(무효 프레임) 이전 값을 그대로 유지한다 (계획 §6).
     */
    fun updateFrameResult(result: FrameResult) {
        val current = _state.value as? MeasurementState.Tracking ?: return
        _state.value = current.copy(
            box1 = result.box1?.viewRect ?: current.box1,
            box1Lost = result.box1?.isLost ?: current.box1Lost,
            box2 = result.box2?.viewRect ?: current.box2,
            box2Lost = result.box2?.isLost ?: current.box2Lost,
            distanceMeters = result.distanceMeters ?: current.distanceMeters,
            // distanceMeters가 새로 갱신될 때만 신뢰도 플래그도 함께 갱신한다.
            distanceLowConfidence = if (result.distanceMeters != null) {
                result.distanceLowConfidence
            } else {
                current.distanceLowConfidence
            },
        )
    }

    /** 다중 시점 삼각측량 스냅샷 정밀측정 진행 상태를 반영한다. */
    fun updatePrecisionCapture(captureState: PrecisionCaptureState) {
        val current = _state.value as? MeasurementState.Tracking ?: return
        _state.value = current.copy(precisionCapture = captureState)
    }

    fun reset() {
        _state.value = MeasurementState.SelectingMode
    }
}
