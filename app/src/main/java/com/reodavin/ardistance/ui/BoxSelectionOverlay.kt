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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.reodavin.ardistance.pipeline.MeasurementMode
import com.reodavin.ardistance.pipeline.MeasurementState
import com.reodavin.ardistance.pipeline.PixelRect

private const val MIN_BOX_SIZE_PX = 20f
private val BOX1_COLOR = Color(0xFFFF5252) // 빨강
private val BOX2_COLOR = Color(0xFF448AFF) // 파랑
private val DISTANCE_LINE_COLOR = Color(0xFFFFEB3B) // 노랑

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
    val textMeasurer = rememberTextMeasurer()

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
                drawTrackedDistance(textMeasurer, state)
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

/**
 * 거리를 화면 정중앙 고정 텍스트 대신 **실제 추적 위치**에 선+라벨로 그린다 (TODO §3D 렌더링).
 * 2타겟 모드: 박스1↔박스2 중심을 잇는 선. 1타겟 모드: 화면 하단 중앙(카메라/사용자 위치를 대신
 * 표현)에서 박스1 중심까지의 선. 두 경우 모두 박스가 트래커를 따라 움직이면 선/라벨도 같이
 * 움직여 "사물에 붙어있는 자"처럼 보인다. 완전한 3D OpenGL 렌더링(ARCore 앵커+빌보드 텍스트)
 * 대신 이미 추적 중인 화면 좌표를 그대로 활용하는 경량 방식이다.
 */
private fun DrawScope.drawTrackedDistance(textMeasurer: TextMeasurer, state: MeasurementState.Tracking) {
    val box1 = state.box1 ?: return
    val start: Offset
    val end: Offset
    when (state.mode) {
        MeasurementMode.TWO_TARGET -> {
            val box2 = state.box2 ?: return
            start = Offset(box1.centerX, box1.centerY)
            end = Offset(box2.centerX, box2.centerY)
        }
        MeasurementMode.ONE_TARGET -> {
            start = Offset(size.width / 2f, size.height) // 화면 하단 중앙 = 카메라(사용자) 위치
            end = Offset(box1.centerX, box1.centerY)
        }
    }

    drawLine(color = DISTANCE_LINE_COLOR, start = start, end = end, strokeWidth = 3f)

    val label = state.distanceMeters?.let { "%.2f m".format(it) } ?: "측정 중…"
    val layout = textMeasurer.measure(label, style = TextStyle(color = DISTANCE_LINE_COLOR, fontSize = 20.sp))
    val midpoint = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
    val textTopLeft = Offset(
        midpoint.x - layout.size.width / 2f,
        midpoint.y - layout.size.height / 2f,
    )

    drawRect(
        color = Color.Black.copy(alpha = 0.6f),
        topLeft = textTopLeft - Offset(8f, 4f),
        size = Size(layout.size.width + 16f, layout.size.height + 8f),
    )
    drawText(textLayoutResult = layout, topLeft = textTopLeft)
}
