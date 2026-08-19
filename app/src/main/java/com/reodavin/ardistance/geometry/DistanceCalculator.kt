package com.reodavin.ardistance.geometry

import kotlin.math.sqrt

/**
 * 두 월드 좌표 사이의 유클리드 거리를 계산하고 지수이동평균(EMA)으로 스무딩한다
 * (계획 §5.3, §6 — depth 노이즈로 인한 화면 값 떨림 완화).
 */
class DistanceCalculator(private val smoothingAlpha: Float = 0.3f) {
    private var smoothedMeters: Float? = null

    fun update(worldPoint1: FloatArray, worldPoint2: FloatArray): Float {
        val dx = worldPoint1[0] - worldPoint2[0]
        val dy = worldPoint1[1] - worldPoint2[1]
        val dz = worldPoint1[2] - worldPoint2[2]
        val raw = sqrt(dx * dx + dy * dy + dz * dz)

        val prev = smoothedMeters
        val next = if (prev == null) raw else prev + smoothingAlpha * (raw - prev)
        smoothedMeters = next
        return next
    }

    fun reset() {
        smoothedMeters = null
    }
}
