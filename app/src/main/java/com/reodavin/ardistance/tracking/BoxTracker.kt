package com.reodavin.ardistance.tracking

import com.reodavin.ardistance.pipeline.PixelRect
import org.opencv.core.Mat

/** [gray]와 [PixelRect]는 항상 ARCore의 IMAGE_PIXELS 좌표계(카메라 이미지 픽셀) 기준이다. */
interface BoxTracker {
    fun init(gray: Mat, roi: PixelRect)
    fun update(gray: Mat): TrackResult

    /** 트래커가 내부적으로 들고 있는 OpenCV Mat 등 네이티브 리소스를 해제한다. */
    fun release() {}
}

data class TrackResult(val rect: PixelRect?, val confidence: Float, val isLost: Boolean)
