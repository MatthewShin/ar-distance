package com.reodavin.ardistance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
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
import com.reodavin.ardistance.ui.theme.Box1Color
import com.reodavin.ardistance.ui.theme.Box2Color
import com.reodavin.ardistance.ui.theme.DistanceLineColor

private const val MIN_BOX_SIZE_PX = 20f
private const val TAP_BOX_RADIUS_PX = 70f
private val BOX1_COLOR = Box1Color
private val BOX2_COLOR = Box2Color
private val DISTANCE_LINE_COLOR = DistanceLineColor

/**
 * 드래그(또는 탭)로 박스1 → 박스2를 순서대로 지정하는 오버레이 (계획 §3).
 * [MeasurementState.Tracking] 상태에서는 [FrameProcessor]가 갱신하는 박스 위치를 그대로 그리고
 * 추가 드래그는 무시한다 (재지정은 초기화 버튼으로만 가능).
 *
 * 드래그 크기가 [MIN_BOX_SIZE_PX] 미만이면(=거의 움직이지 않은 탭) 그 지점을 중심으로
 * 반지름 [TAP_BOX_RADIUS_PX]의 고정 크기 박스를 만든다 — 별도 모드 전환 없이 드래그와 탭을
 * 한 제스처로 통합한다. 사물 크기에 맞춰 정밀하게 지정하고 싶으면 드래그를, 빠르게 점 하나만
 * 찍고 싶으면 탭을 쓰면 된다.
 *
 * [zoomFactor]는 [com.reodavin.ardistance.ar.CameraBackgroundRenderer]가 그리는 디지털 줌 배율과
 * 동일한 값을 받는다(1.0 = 줌 없음). 줌은 화면 표시/터치 좌표 해석에만 관여하고
 * [FrameProcessor]의 트래킹/거리 계산은 항상 줌 없는 논리 좌표계로 동작하므로, 이 오버레이가
 * 화면(줌 적용) 좌표 ↔ 논리(줌 없음) 좌표를 양방향으로 변환해준다.
 */
@Composable
fun BoxSelectionOverlay(
    state: MeasurementState,
    onBoxConfirmed: (PixelRect) -> Unit,
    modifier: Modifier = Modifier,
    zoomFactor: Float = 1f,
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    val canDraw = state is MeasurementState.SelectingBox1 || state is MeasurementState.SelectingBox2
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(canDraw, zoomFactor) {
                if (!canDraw) return@pointerInput
                val center = Offset(size.width / 2f, size.height / 2f)
                // detectDragGestures는 터치 슬롭(기기별 ~18dp)을 넘지 않으면 onDragStart 자체가
                // 호출되지 않아 "제자리 탭"을 아예 놓친다. 탭도 확실히 잡기 위해 awaitEachGesture로
                // 직접 구현: 손을 뗄 때까지 슬롭을 못 넘으면 탭(고정 크기 박스), 넘으면 드래그로 처리.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragStart = down.position
                    dragCurrent = down.position

                    val slopChange = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                        dragCurrent = change.position
                    }

                    if (slopChange != null) {
                        drag(slopChange.id) { change ->
                            dragCurrent = change.position
                            change.consume()
                        }
                    }

                    // 화면(터치, 줌 적용) 좌표 기준으로 드래그인지 탭인지 판단하고,
                    // 실제 트래커 init에는 논리(줌 없음) 좌표로 변환해 넘긴다.
                    val screenRect = boundingRect(dragStart, dragCurrent)
                    val finalRect = if (screenRect != null &&
                        screenRect.width >= MIN_BOX_SIZE_PX &&
                        screenRect.height >= MIN_BOX_SIZE_PX
                    ) {
                        screenRect
                    } else {
                        dragStart?.let { tapPoint -> fixedBoxAt(tapPoint, TAP_BOX_RADIUS_PX) }
                    }
                    finalRect?.let { onBoxConfirmed(it.zoomedOut(center, zoomFactor)) }
                    dragStart = null
                    dragCurrent = null
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)

        when (state) {
            is MeasurementState.SelectingMode -> Unit
            is MeasurementState.SelectingBox1 -> Unit
            is MeasurementState.SelectingBox2 -> {
                drawBoxOutline(state.box1.zoomedIn(center, zoomFactor), BOX1_COLOR, dimmed = false)
            }
            is MeasurementState.Tracking -> {
                state.box1?.let { drawBoxOutline(it.zoomedIn(center, zoomFactor), BOX1_COLOR, dimmed = state.box1Lost) }
                state.box2?.let { drawBoxOutline(it.zoomedIn(center, zoomFactor), BOX2_COLOR, dimmed = state.box2Lost) }
                drawTrackedDistance(textMeasurer, state, zoomFactor)
            }
        }

        // 드래그 미리보기는 사용자가 지금 화면에서 보는 그대로 그리므로 줌 변환이 필요 없다.
        if (canDraw) {
            boundingRect(dragStart, dragCurrent)?.let { preview ->
                val previewColor = if (state is MeasurementState.SelectingBox1) BOX1_COLOR else BOX2_COLOR
                drawBoxOutline(preview, previewColor, dimmed = false, alpha = 0.6f)
            }
        }
    }
}

/** 탭 지점을 중심으로 한 고정 크기(반지름 [radius]) 정사각형 박스. */
private fun fixedBoxAt(point: Offset, radius: Float): PixelRect {
    return PixelRect(
        left = point.x - radius,
        top = point.y - radius,
        right = point.x + radius,
        bottom = point.y + radius,
    )
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

/** 화면(줌 적용) 좌표 → 논리(줌 없음) VIEW 좌표. 박스 드래그 확정 시 사용. */
private fun PixelRect.zoomedOut(center: Offset, zoom: Float): PixelRect {
    return PixelRect(
        left = center.x + (left - center.x) / zoom,
        top = center.y + (top - center.y) / zoom,
        right = center.x + (right - center.x) / zoom,
        bottom = center.y + (bottom - center.y) / zoom,
    )
}

/** 논리(줌 없음) VIEW 좌표 → 화면(줌 적용) 좌표. 트래킹 결과를 그릴 때 사용. */
private fun PixelRect.zoomedIn(center: Offset, zoom: Float): PixelRect {
    return PixelRect(
        left = center.x + (left - center.x) * zoom,
        top = center.y + (top - center.y) * zoom,
        right = center.x + (right - center.x) * zoom,
        bottom = center.y + (bottom - center.y) * zoom,
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
private fun DrawScope.drawTrackedDistance(textMeasurer: TextMeasurer, state: MeasurementState.Tracking, zoomFactor: Float) {
    val box1 = state.box1 ?: return
    val center = Offset(size.width / 2f, size.height / 2f)
    val box1Screen = box1.zoomedIn(center, zoomFactor)

    val start: Offset
    val end: Offset
    when (state.mode) {
        MeasurementMode.TWO_TARGET -> {
            val box2 = state.box2 ?: return
            val box2Screen = box2.zoomedIn(center, zoomFactor)
            start = Offset(box1Screen.centerX, box1Screen.centerY)
            end = Offset(box2Screen.centerX, box2Screen.centerY)
        }
        MeasurementMode.ONE_TARGET -> {
            // 화면 하단 중앙 = 카메라(사용자) 위치. 화면 자체의 고정 지점이므로 줌 변환 불필요.
            start = Offset(size.width / 2f, size.height)
            end = Offset(box1Screen.centerX, box1Screen.centerY)
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
