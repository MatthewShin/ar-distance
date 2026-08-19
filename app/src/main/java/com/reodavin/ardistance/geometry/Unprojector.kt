package com.reodavin.ardistance.geometry

/**
 * 픽셀(u, v — 카메라 이미지 전체 해상도 기준) + depth(미터) + 카메라 intrinsics로
 * ARCore 카메라 로컬 좌표계 3D 점을 계산한다 (핀홀 역투영, 계획 §5.1).
 *
 * ARCore 카메라 로컬 좌표계는 OpenGL 컨벤션을 따른다: 카메라는 -Z 방향을 바라보고,
 * +Y는 위쪽이다. 반면 이미지 좌표계는 v가 아래로 증가하므로 Y 부호를 반전해야 하고,
 * depth(항상 양수, 카메라 앞 방향 거리)는 -Z에 대응한다.
 */
object Unprojector {
    data class Intrinsics(val fx: Float, val fy: Float, val cx: Float, val cy: Float)

    fun unproject(u: Float, v: Float, depthMeters: Float, intrinsics: Intrinsics): FloatArray {
        val x = (u - intrinsics.cx) * depthMeters / intrinsics.fx
        val y = (v - intrinsics.cy) * depthMeters / intrinsics.fy
        return floatArrayOf(x, -y, -depthMeters)
    }
}
