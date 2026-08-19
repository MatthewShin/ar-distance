package com.reodavin.ardistance.geometry

import com.google.ar.core.Pose

/** 카메라 로컬 좌표계 3D 점을 ARCore 월드 좌표계(실측 미터 스케일)로 변환한다 (계획 §5.2). */
object PoseTransform {
    fun toWorld(cameraSpacePoint: FloatArray, cameraPose: Pose): FloatArray {
        return cameraPose.transformPoint(cameraSpacePoint)
    }
}
