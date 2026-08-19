package com.reodavin.ardistance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.reodavin.ardistance.pipeline.MeasurementState
import com.reodavin.ardistance.pipeline.PixelRect

private const val MIN_BOX_SIZE_PX = 20f
private val BOX1_COLOR = Color(0xFFFF5252) // 빨강
private val BOX2_COLOR = Color(0xFF448AFF) // 파랑

/**
 * 드래그로 박스1 → 박스2를 순서대로 지정하는 오버레이 (계획 §3).
 * [MeasurementState.Tracking] 상태에서는 [FrameProcessor]가 갱신하는 박스 위치를 그대로 그리고
 * 추가 드래그는 무시한다 (재지정은 초기화 버튼으로만 가능).
 */
@Composable
fun BoxSelectionOverlay(
    state: MeasurementState,
    onBoxConfirmed: (PixelRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    val canDraw = state is MeasurementState.SelectingBox1 || state is MeasurementState.SelectingBox2

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(canDraw) {
                if (!canDraw) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStart = offset
                        dragCurrent = offset
                    },
                    onDrag = { change, _ ->
                        dragCurrent = change.position
                    },
                    onDragEnd = {
                        val rect = boundingRect(dragStart, dragCurrent)
                        if (rect != null && rect.width >= MIN_BOX_SIZE_PX && rect.height >= MIN_BOX_SIZE_PX) {
                            onBoxConfirmed(rect)
                        }
                        dragStart = null
                        dragCurrent = null
                    },
                    onDragCancel = {
                        dragStart = null
                        dragCurrent = null
                    },
                )
            },
    ) {
        when (state) {
            is MeasurementState.SelectingMode -> Unit
            is MeasurementState.SelectingBox1 -> Unit
            is MeasurementState.SelectingBox2 -> drawBoxOutline(state.box1, BOX1_COLOR, dimmed = false)
            is MeasurementState.Tracking -> {
                state.box1?.let { drawBoxOutline(it, BOX1_COLOR, dimmed = state.box1Lost) }
                state.box2?.let { drawBoxOutline(it, BOX2_COLOR, dimmed = state.box2Lost) }
            }
        }

        if (canDraw) {
            boundingRect(dragStart, dragCurrent)?.let { preview ->
                val previewColor = if (state is MeasurementState.SelectingBox1) BOX1_COLOR else BOX2_COLOR
                drawBoxOutline(preview, previewColor, dimmed = false, alpha = 0.6f)
            }
        }
    }
}

private fun boundingRect(start: Offset?, end: Offset?): PixelRect? {
    if (start == null || end == null) return null
    return PixelRect(
        left = minOf(start.x, end.x),
        top = minOf(start.y, end.y),
        right = maxOf(start.x, end.x),
        bottom = maxOf(start.y, end.y),
    )
}

private fun DrawScope.drawBoxOutline(rect: PixelRect, color: Color, dimmed: Boolean, alpha: Float = 1f) {
    drawRect(
        color = color.copy(alpha = if (dimmed) 0.3f else alpha),
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        style = Stroke(width = 4f),
    )
}
