package com.reodavin.ardistance.pipeline

/**
 * 앱 전체(UI 드래그 좌표, ARCore VIEW/IMAGE_PIXELS 좌표, CV 트래커 결과)에서 공용으로 쓰는
 * 2D 사각형. 어떤 좌표계 기준인지는 사용하는 쪽의 문맥으로 구분한다
 * (화면 표시는 VIEW 좌표계, [com.reodavin.ardistance.tracking.BoxTracker]는 IMAGE_PIXELS 좌표계).
 */
data class PixelRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}
