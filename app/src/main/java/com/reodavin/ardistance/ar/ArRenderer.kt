package com.reodavin.ardistance.ar

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.reodavin.ardistance.pipeline.FrameProcessor
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 카메라 배경을 그리고 [ArSessionManager]로부터 매 프레임 [com.google.ar.core.Frame]을 얻어
 * 카메라 트래킹 상태를 콜백으로 전달한다. 매 프레임 [frameProcessor]에 CPU 이미지 처리(박스
 * 추적, M4부터는 depth 조회까지)를 위임한다 — 모두 이 GL 스레드에서 동기적으로 실행된다
 * (계획 §4.2: 렌더링 블로킹 방지를 위한 별도 스레드 분리는 M5 폴리싱 단계로 미뤘다).
 */
class ArRenderer(
    private val sessionManager: ArSessionManager,
    private val frameProcessor: FrameProcessor,
    private val onFrameStatus: (FrameStatus) -> Unit,
) : GLSurfaceView.Renderer {

    private val backgroundRenderer = CameraBackgroundRenderer()
    private var viewportWidth = 0
    private var viewportHeight = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        backgroundRenderer.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val session = sessionManager.session ?: return
        session.setCameraTextureName(backgroundRenderer.textureId)

        if (viewportWidth > 0 && viewportHeight > 0) {
            session.setDisplayGeometry(displayRotation(), viewportWidth, viewportHeight)
        }

        val frame = try {
            session.update()
        } catch (e: Exception) {
            Log.e(TAG, "session.update() 실패", e)
            return
        }

        backgroundRenderer.updateTexCoords(frame)
        backgroundRenderer.draw()

        try {
            frameProcessor.process(frame)
        } catch (e: Exception) {
            Log.e(TAG, "frameProcessor.process() 실패", e)
        }

        val camera = frame.camera
        onFrameStatus(
            FrameStatus(
                trackingState = camera.trackingState,
                failureReason = if (camera.trackingState == TrackingState.PAUSED) {
                    camera.trackingFailureReason
                } else {
                    TrackingFailureReason.NONE
                },
            )
        )
    }

    /** GLSurfaceView는 항상 세로 고정(Manifest에서 portrait 고정)이므로 0(ROTATION_0)을 사용한다. */
    private fun displayRotation(): Int = android.view.Surface.ROTATION_0

    data class FrameStatus(
        val trackingState: TrackingState,
        val failureReason: TrackingFailureReason,
    )

    companion object {
        private const val TAG = "ArRenderer"
    }
}
