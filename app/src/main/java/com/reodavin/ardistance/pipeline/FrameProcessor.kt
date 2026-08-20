package com.reodavin.ardistance.pipeline

import android.media.Image
import android.util.Log
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import com.reodavin.ardistance.geometry.DistanceCalculator
import com.reodavin.ardistance.geometry.PoseTransform
import com.reodavin.ardistance.geometry.Unprojector
import com.reodavin.ardistance.tracking.BoxTracker
import com.reodavin.ardistance.tracking.OpenCvLoader
import com.reodavin.ardistance.tracking.TrackerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.min
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect as CvRect

/** [viewRect]는 이번 프레임에 새로 갱신된 값일 때만 non-null (놓쳤거나 트래커가 없으면 null). */
data class TrackedBox(val viewRect: PixelRect?, val isLost: Boolean)

/**
 * [distanceMeters]는 이번 프레임에 새로 계산됐을 때만 non-null — 무효 프레임은 이전 값을 유지해야 한다(계획 §6).
 * [distanceLowConfidence]도 [distanceMeters]가 non-null일 때만 의미 있다 — ARCore의 raw depth
 * confidence가 낮은 샘플이 많았다는 뜻으로, 장거리/저텍스처 등에서 값을 신뢰하기 어려울 수 있다.
 */
data class FrameResult(
    val box1: TrackedBox?,
    val box2: TrackedBox?,
    val distanceMeters: Float?,
    val distanceLowConfidence: Boolean = false,
)

/** [sampleDepthMeters]의 결과 — depth 값과 함께, 샘플 중 신뢰도 높은 픽셀의 비율을 담는다. */
private data class DepthSample(val depthMeters: Float, val highConfidenceRatio: Float)

/** 두 지점을 이용한 거리 계산 결과. */
private data class DistanceMeasurement(val meters: Float, val lowConfidence: Boolean)

/**
 * 매 프레임 카메라 CPU 이미지를 그레이스케일 Mat으로 변환해 박스 트래커(인덱스 0/1)를 갱신하고,
 * 두 박스 중심의 depth를 조회해 실측 거리(미터)를 계산한다 (계획 §4.5, §5, §7).
 *
 * 좌표는 항상 화면(VIEW) 좌표계로 입출력하며, 내부적으로만 ARCore의
 * IMAGE_PIXELS 좌표계로 변환해 트래커/depth 조회에 사용한다.
 *
 * [process]는 GL 스레드([com.reodavin.ardistance.ar.ArRenderer.onDrawFrame])에서만 호출해야 한다.
 * [requestInit]/[resetAll]은 다른 스레드(Compose UI)에서 호출되므로 커맨드 큐로 넘긴다.
 */
class FrameProcessor(
    private val onResult: (FrameResult) -> Unit,
) {
    private sealed class Command {
        data class Init(val index: Int, val viewRect: PixelRect) : Command()
        data class SetMode(val mode: MeasurementMode) : Command()
        data object Reset : Command()
    }

    private val commandQueue = ConcurrentLinkedQueue<Command>()
    private val trackers = arrayOfNulls<BoxTracker>(2)
    private val distanceCalculator = DistanceCalculator()
    private var mode = MeasurementMode.TWO_TARGET

    fun requestInit(index: Int, viewRect: PixelRect) {
        commandQueue.add(Command.Init(index, viewRect))
    }

    fun setMode(mode: MeasurementMode) {
        commandQueue.add(Command.SetMode(mode))
    }

    fun resetAll() {
        commandQueue.add(Command.Reset)
    }

    fun process(frame: Frame) {
        if (!OpenCvLoader.ensureInitialized()) return
        if (frame.camera.trackingState != TrackingState.TRACKING) return

        val commands = mutableListOf<Command>()
        while (true) {
            commands.add(commandQueue.poll() ?: break)
        }

        if (commands.isEmpty() && trackers[0] == null && trackers[1] == null) return

        val image = try {
            frame.acquireCameraImage()
        } catch (e: NotYetAvailableException) {
            return
        } catch (e: Exception) {
            Log.e(TAG, "카메라 이미지 획득 실패", e)
            return
        }

        // image.close() 이후에는 width/height 조회가 불안정할 수 있어 먼저 값을 복사해둔다.
        val cameraImageWidth = image.width
        val cameraImageHeight = image.height

        val gray = try {
            imageToGrayMat(image)
        } finally {
            image.close()
        }

        try {
            for (command in commands) {
                when (command) {
                    is Command.Reset -> {
                        trackers[0]?.release()
                        trackers[1]?.release()
                        trackers[0] = null
                        trackers[1] = null
                        distanceCalculator.reset()
                    }
                    is Command.SetMode -> {
                        mode = command.mode
                    }
                    is Command.Init -> {
                        val imageRect = viewRectToImageRect(frame, command.viewRect)
                        val tracker = TrackerFactory.create()
                        tracker.init(gray, imageRect)
                        trackers[command.index]?.release()
                        trackers[command.index] = tracker
                    }
                }
            }

            val imageRects = arrayOfNulls<PixelRect>(2)
            val trackedBoxes = arrayOfNulls<TrackedBox>(2)

            for (index in 0..1) {
                val tracker = trackers[index] ?: continue
                val result = tracker.update(gray)
                val viewRect = result.rect?.let { imageRectToViewRect(frame, it) }
                trackedBoxes[index] = TrackedBox(viewRect, result.isLost)
                if (!result.isLost) {
                    imageRects[index] = result.rect
                }
            }

            val measurement = when (mode) {
                MeasurementMode.ONE_TARGET -> computeDistanceToCameraMeters(
                    frame = frame,
                    cameraImageWidth = cameraImageWidth,
                    cameraImageHeight = cameraImageHeight,
                    rect1 = imageRects[0],
                )
                MeasurementMode.TWO_TARGET -> computeDistanceBetweenTargetsMeters(
                    frame = frame,
                    cameraImageWidth = cameraImageWidth,
                    cameraImageHeight = cameraImageHeight,
                    rect1 = imageRects[0],
                    rect2 = imageRects[1],
                )
            }

            onResult(
                FrameResult(
                    box1 = trackedBoxes[0],
                    box2 = trackedBoxes[1],
                    distanceMeters = measurement?.meters,
                    distanceLowConfidence = measurement?.lowConfidence ?: false,
                )
            )
        } finally {
            gray.release()
        }
    }

    /** 2타겟 모드: 두 사물의 world 좌표 사이 거리. */
    private fun computeDistanceBetweenTargetsMeters(
        frame: Frame,
        cameraImageWidth: Int,
        cameraImageHeight: Int,
        rect1: PixelRect?,
        rect2: PixelRect?,
    ): DistanceMeasurement? {
        if (rect1 == null || rect2 == null) return null

        val (depthImage, confidenceImage) = acquireRawDepthWithConfidence(frame) ?: return null

        return try {
            val intr = imageIntrinsicsOf(frame)
            val sample1 = sampleDepthMeters(depthImage, confidenceImage, rect1, cameraImageWidth, cameraImageHeight)
            val sample2 = sampleDepthMeters(depthImage, confidenceImage, rect2, cameraImageWidth, cameraImageHeight)
            if (sample1 == null || sample2 == null) return null

            val camPoint1 = Unprojector.unproject(rect1.centerX, rect1.centerY, sample1.depthMeters, intr)
            val camPoint2 = Unprojector.unproject(rect2.centerX, rect2.centerY, sample2.depthMeters, intr)
            val worldPoint1 = PoseTransform.toWorld(camPoint1, frame.camera.pose)
            val worldPoint2 = PoseTransform.toWorld(camPoint2, frame.camera.pose)

            val meters = distanceCalculator.update(worldPoint1, worldPoint2)
            val lowConfidence = sample1.highConfidenceRatio < MIN_HIGH_CONFIDENCE_RATIO ||
                sample2.highConfidenceRatio < MIN_HIGH_CONFIDENCE_RATIO
            DistanceMeasurement(meters, lowConfidence)
        } finally {
            depthImage.close()
            confidenceImage.close()
        }
    }

    /**
     * 1타겟 모드: 카메라(사용자)로부터 사물까지의 거리.
     * 카메라 로컬 좌표계의 원점이 곧 카메라 위치이므로, 역투영된 점의 벡터 길이가 바로 그 거리다
     * (월드 좌표 변환이 필요 없다).
     */
    private fun computeDistanceToCameraMeters(
        frame: Frame,
        cameraImageWidth: Int,
        cameraImageHeight: Int,
        rect1: PixelRect?,
    ): DistanceMeasurement? {
        if (rect1 == null) return null

        val (depthImage, confidenceImage) = acquireRawDepthWithConfidence(frame) ?: return null

        return try {
            val intr = imageIntrinsicsOf(frame)
            val sample1 = sampleDepthMeters(depthImage, confidenceImage, rect1, cameraImageWidth, cameraImageHeight)
                ?: return null
            val camPoint1 = Unprojector.unproject(rect1.centerX, rect1.centerY, sample1.depthMeters, intr)
            val meters = distanceCalculator.update(camPoint1, CAMERA_ORIGIN)
            DistanceMeasurement(meters, sample1.highConfidenceRatio < MIN_HIGH_CONFIDENCE_RATIO)
        } finally {
            depthImage.close()
            confidenceImage.close()
        }
    }

    /**
     * ARCore의 raw depth(필터링 전 원본)와, 픽셀별 신뢰도(0~255, [Frame.acquireRawDepthConfidenceImage])를
     * 함께 가져온다. smoothed depth([Frame.acquireDepthImage16Bits])는 신뢰도 정보가 없어 저신뢰
     * 픽셀을 걸러낼 수 없으므로, 신뢰도 기반 필터링을 위해 raw 쪽으로 전환했다.
     */
    private fun acquireRawDepthWithConfidence(frame: Frame): Pair<Image, Image>? {
        return try {
            val depthImage = frame.acquireRawDepthImage16Bits()
            try {
                val confidenceImage = frame.acquireRawDepthConfidenceImage()
                depthImage to confidenceImage
            } catch (e: Exception) {
                depthImage.close()
                null
            }
        } catch (e: Exception) {
            // Depth API 미지원 기기, 아직 raw depth 프레임 미준비 등 (계획 §7) — 조용히 이번 프레임은 건너뜀.
            null
        }
    }

    private fun imageIntrinsicsOf(frame: Frame): Unprojector.Intrinsics {
        val intrinsics = frame.camera.imageIntrinsics
        val focal = intrinsics.focalLength
        val principal = intrinsics.principalPoint
        return Unprojector.Intrinsics(fx = focal[0], fy = focal[1], cx = principal[0], cy = principal[1])
    }

    /**
     * depth/confidence 이미지는 카메라 이미지보다 해상도가 낮은 경우가 많아 좌표를 비율로 스케일링한다 (계획 §4.5).
     *
     * 박스 내부(가장자리는 배경이 섞일 수 있어 [INSET_RATIO]만큼 안쪽으로 줄인 영역)에
     * [GRID_SIZE] x [GRID_SIZE] 격자로 다중 포인트를 샘플링한다. 각 포인트마다 ARCore가 제공하는
     * raw depth 신뢰도(0~255)도 함께 읽어서, 신뢰도가 [CONFIDENCE_THRESHOLD] 미만인 픽셀은
     * "믿을 수 없는 값"으로 보고 최종 대표값(median) 계산에서 제외한다 — 장거리/저텍스처 등
     * ARCore가 스스로 부정확하다고 판단한 픽셀을 걸러내는 것이다. 고신뢰 샘플이 너무 적으면
     * (전체 대비 [MIN_HIGH_CONFIDENCE_RATIO] 미만) 신뢰도 필터링 없이 전체 유효 샘플을 대신 쓰되,
     * 호출부가 [DepthSample.highConfidenceRatio]로 "이 값은 신뢰도가 낮다"는 걸 알 수 있게 한다.
     */
    private fun sampleDepthMeters(
        depthImage: Image,
        confidenceImage: Image,
        rect: PixelRect,
        cameraImageWidth: Int,
        cameraImageHeight: Int,
    ): DepthSample? {
        val depthWidth = depthImage.width
        val depthHeight = depthImage.height
        val depthPlane = depthImage.planes[0]
        val depthBuffer = depthPlane.buffer
        val depthRowStride = depthPlane.rowStride
        val depthPixelStride = depthPlane.pixelStride
        val depthLimit = depthBuffer.limit()

        val confWidth = confidenceImage.width
        val confHeight = confidenceImage.height
        val confPlane = confidenceImage.planes[0]
        val confBuffer = confPlane.buffer
        val confRowStride = confPlane.rowStride
        val confPixelStride = confPlane.pixelStride
        val confLimit = confBuffer.limit()

        val insetLeft = rect.left + rect.width * INSET_RATIO
        val insetTop = rect.top + rect.height * INSET_RATIO
        val insetWidth = rect.width * (1f - 2 * INSET_RATIO)
        val insetHeight = rect.height * (1f - 2 * INSET_RATIO)

        val allSamplesMm = mutableListOf<Int>()
        val highConfSamplesMm = mutableListOf<Int>()
        for (gy in 0 until GRID_SIZE) {
            for (gx in 0 until GRID_SIZE) {
                val u = insetLeft + insetWidth * (gx + 0.5f) / GRID_SIZE
                val v = insetTop + insetHeight * (gy + 0.5f) / GRID_SIZE

                val depthX = ((u / cameraImageWidth) * depthWidth).toInt().coerceIn(0, depthWidth - 1)
                val depthY = ((v / cameraImageHeight) * depthHeight).toInt().coerceIn(0, depthHeight - 1)
                val depthByteIndex = depthY * depthRowStride + depthX * depthPixelStride
                if (depthByteIndex < 0 || depthByteIndex + 1 >= depthLimit) continue

                val low = depthBuffer.get(depthByteIndex).toInt() and 0xFF
                val high = depthBuffer.get(depthByteIndex + 1).toInt() and 0xFF
                val depthMm = (high shl 8) or low
                if (depthMm <= 0) continue
                allSamplesMm.add(depthMm)

                val confX = ((u / cameraImageWidth) * confWidth).toInt().coerceIn(0, confWidth - 1)
                val confY = ((v / cameraImageHeight) * confHeight).toInt().coerceIn(0, confHeight - 1)
                val confByteIndex = confY * confRowStride + confX * confPixelStride
                if (confByteIndex < 0 || confByteIndex >= confLimit) continue
                val confidence = confBuffer.get(confByteIndex).toInt() and 0xFF
                if (confidence >= CONFIDENCE_THRESHOLD) highConfSamplesMm.add(depthMm)
            }
        }

        if (allSamplesMm.isEmpty()) return null

        val highConfidenceRatio = highConfSamplesMm.size.toFloat() / allSamplesMm.size
        val sourceMm = if (highConfSamplesMm.size >= MIN_HIGH_CONF_SAMPLE_COUNT) highConfSamplesMm else allSamplesMm
        sourceMm.sort()

        val trimCount = (sourceMm.size * TRIM_RATIO).toInt()
        val trimmed = if (sourceMm.size - trimCount * 2 >= 1) {
            sourceMm.subList(trimCount, sourceMm.size - trimCount)
        } else {
            sourceMm
        }
        val medianMm = trimmed[trimmed.size / 2]
        return DepthSample(depthMeters = medianMm / 1000f, highConfidenceRatio = highConfidenceRatio)
    }

    private fun imageToGrayMat(image: Image): Mat {
        val yPlane = image.planes[0]
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val width = image.width
        val height = image.height
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val fullMat = Mat(height, rowStride, CvType.CV_8UC1)
        fullMat.put(0, 0, bytes)
        if (rowStride == width) return fullMat

        val cropped = Mat(fullMat, CvRect(0, 0, width, height)).clone()
        fullMat.release()
        return cropped
    }

    private fun viewRectToImageRect(frame: Frame, viewRect: PixelRect): PixelRect {
        return transformRect(frame, viewRect, Coordinates2d.VIEW, Coordinates2d.IMAGE_PIXELS)
    }

    private fun imageRectToViewRect(frame: Frame, imageRect: PixelRect): PixelRect {
        return transformRect(frame, imageRect, Coordinates2d.IMAGE_PIXELS, Coordinates2d.VIEW)
    }

    private fun transformRect(
        frame: Frame,
        rect: PixelRect,
        from: Coordinates2d,
        to: Coordinates2d,
    ): PixelRect {
        val input = floatArrayOf(rect.left, rect.top, rect.right, rect.bottom)
        val output = FloatArray(4)
        frame.transformCoordinates2d(from, input, to, output)
        return PixelRect(
            left = min(output[0], output[2]),
            top = min(output[1], output[3]),
            right = max(output[0], output[2]),
            bottom = max(output[1], output[3]),
        )
    }

    companion object {
        private const val TAG = "FrameProcessor"
        private val CAMERA_ORIGIN = floatArrayOf(0f, 0f, 0f)

        /** depth 다중 포인트 샘플링 격자 크기 (GRID_SIZE x GRID_SIZE 포인트). */
        private const val GRID_SIZE = 5

        /** 박스 가장자리(배경이 섞일 위험)를 피하기 위해 각 방향으로 안쪽으로 줄이는 비율. */
        private const val INSET_RATIO = 0.15f

        /** 정렬된 depth 샘플의 상하위 이 비율만큼을 이상치로 보고 잘라낸다. */
        private const val TRIM_RATIO = 0.2f

        /** raw depth confidence(0~255) 값이 이 이상이면 "신뢰 가능한" 픽셀로 본다. */
        private const val CONFIDENCE_THRESHOLD = 128

        /** 고신뢰 샘플이 이 개수 미만이면 필터링 없이 전체 샘플을 대신 사용한다(값이 아예 없는 것보단 낫다). */
        private const val MIN_HIGH_CONF_SAMPLE_COUNT = 5

        /** 전체 샘플 중 고신뢰 비율이 이 값 미만이면 [DistanceMeasurement.lowConfidence]를 true로 표시한다. */
        private const val MIN_HIGH_CONFIDENCE_RATIO = 0.4f
    }
}
