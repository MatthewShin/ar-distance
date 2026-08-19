package com.reodavin.ardistance.tracking

import com.reodavin.ardistance.pipeline.PixelRect
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.video.Video
import kotlin.math.max
import kotlin.math.min

/**
 * OpenCV core 모듈(video)만으로 구현한 박스 트래커 (계획 §2.3의 폴백안).
 *
 * ROI 내부에 균등 격자점을 뿌리고 매 프레임 Lucas-Kanade optical
 * flow([Video.calcOpticalFlowPyrLK])로 추적해, 유효 매칭점들의 중앙값 이동량만큼
 * 박스를 평행이동한다. (코너 검출 `Imgproc.goodFeaturesToTrack`이 이 OpenCV 빌드의
 * Java 바인딩에 노출되지 않아 격자점 샘플링으로 대체했다.)
 * 드리프트 완화를 위해 [REDETECT_INTERVAL] 프레임마다 격자점을 재생성한다.
 */
class OpticalFlowBoxTracker : BoxTracker {

    private var prevGray: Mat? = null
    private var points: MatOfPoint2f? = null
    private var currentRoi: PixelRect? = null
    private var framesSinceRedetect = 0

    override fun init(gray: Mat, roi: PixelRect) {
        release()
        currentRoi = roi
        detectFeatures(gray, roi)
        prevGray = gray.clone()
        framesSinceRedetect = 0
    }

    override fun update(gray: Mat): TrackResult {
        val prev = prevGray
        val prevPoints = points
        val roi = currentRoi

        if (prev == null || prevPoints == null || roi == null || prevPoints.rows() == 0) {
            swapPrevGray(gray)
            return TrackResult(rect = roi, confidence = 0f, isLost = true)
        }

        val nextPoints = MatOfPoint2f()
        val status = MatOfByte()
        val err = MatOfFloat()
        Video.calcOpticalFlowPyrLK(prev, gray, prevPoints, nextPoints, status, err)

        val prevArr = prevPoints.toArray()
        val nextArr = nextPoints.toArray()
        val statusArr = status.toArray()
        nextPoints.release()
        status.release()
        err.release()

        val validPrev = mutableListOf<Point>()
        val validNext = mutableListOf<Point>()
        for (i in statusArr.indices) {
            if (statusArr[i].toInt() != 0) {
                validPrev.add(prevArr[i])
                validNext.add(nextArr[i])
            }
        }

        val validRatio = if (prevArr.isEmpty()) 0f else validNext.size.toFloat() / prevArr.size
        swapPrevGray(gray)

        if (validNext.size < MIN_VALID_POINTS || validRatio < MIN_VALID_RATIO) {
            points = pointsOrNull(validNext)
            return TrackResult(rect = roi, confidence = validRatio, isLost = true)
        }

        val dx = validNext.indices.map { validNext[it].x - validPrev[it].x }.sorted()[validNext.size / 2]
        val dy = validNext.indices.map { validNext[it].y - validPrev[it].y }.sorted()[validNext.size / 2]

        val newRoi = PixelRect(
            left = roi.left + dx.toFloat(),
            top = roi.top + dy.toFloat(),
            right = roi.right + dx.toFloat(),
            bottom = roi.bottom + dy.toFloat(),
        )
        currentRoi = newRoi
        points = pointsOrNull(validNext)

        framesSinceRedetect++
        if (framesSinceRedetect >= REDETECT_INTERVAL) {
            framesSinceRedetect = 0
            detectFeatures(gray, newRoi)
        }

        return TrackResult(rect = newRoi, confidence = validRatio, isLost = false)
    }

    override fun release() {
        prevGray?.release()
        prevGray = null
        points?.release()
        points = null
        currentRoi = null
    }

    private fun swapPrevGray(gray: Mat) {
        prevGray?.release()
        prevGray = gray.clone()
    }

    private fun pointsOrNull(list: List<Point>): MatOfPoint2f? {
        return if (list.isEmpty()) null else MatOfPoint2f(*list.toTypedArray())
    }

    /** ROI 내부에 [GRID_SIZE] x [GRID_SIZE] 균등 격자점을 생성한다 (경계에서 살짝 안쪽으로). */
    private fun detectFeatures(gray: Mat, roi: PixelRect) {
        val left = max(0f, roi.left)
        val top = max(0f, roi.top)
        val right = min(gray.cols().toFloat(), roi.right)
        val bottom = min(gray.rows().toFloat(), roi.bottom)
        val w = right - left
        val h = bottom - top
        if (w <= 0f || h <= 0f) {
            points = null
            return
        }

        val gridPoints = mutableListOf<Point>()
        for (r in 0 until GRID_SIZE) {
            for (c in 0 until GRID_SIZE) {
                val x = left + w * (c + 0.5f) / GRID_SIZE
                val y = top + h * (r + 0.5f) / GRID_SIZE
                gridPoints.add(Point(x.toDouble(), y.toDouble()))
            }
        }
        points = pointsOrNull(gridPoints)
    }

    companion object {
        private const val GRID_SIZE = 7
        private const val MIN_VALID_POINTS = 4
        private const val MIN_VALID_RATIO = 0.4f
        private const val REDETECT_INTERVAL = 15
    }
}
