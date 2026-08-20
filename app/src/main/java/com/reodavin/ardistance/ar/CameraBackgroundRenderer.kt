package com.reodavin.ardistance.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * ARCore의 카메라 프레임을 화면 배경으로 그리는 최소 구현.
 *
 * 공식 ARCore 샘플(hello_ar 계열)의 BackgroundRenderer 패턴을 이 앱에 필요한
 * 만큼만 축약했다: 화면 전체를 덮는 사각형에 OES 외부 텍스처(카메라 피드)를
 * 입히고, 매 프레임 [Frame.transformCoordinates2d]로 화면 회전/크롭에 맞는
 * 텍스처 좌표를 다시 계산한다.
 */
class CameraBackgroundRenderer {

    var textureId: Int = -1
        private set

    private var program = 0
    private var positionAttrib = 0
    private var texCoordAttrib = 0
    private var textureUniform = 0

    private val quadCoords: FloatBuffer = floatBufferOf(
        -1f, -1f,
        -1f, +1f,
        +1f, -1f,
        +1f, +1f,
    )

    /** ARCore가 계산한, 줌 미적용 원본 텍스처 좌표 (화면 회전/크롭 반영, 화면 지오메트리 변경 시에만 갱신). */
    private var baseTexCoords: FloatBuffer = floatBufferOf(
        0f, 0f,
        0f, 1f,
        1f, 0f,
        1f, 1f,
    )

    /** 실제 렌더링에 쓰이는 텍스처 좌표 — [baseTexCoords]에 [zoomFactor]를 적용한 결과, 매 프레임 갱신. */
    private var quadTexCoords: FloatBuffer = baseTexCoords

    /** 1.0 = 줌 없음. GL 스레드([updateTexCoords])와 UI 스레드([setZoom]) 양쪽에서 접근하므로 volatile. */
    @Volatile
    private var zoomFactor: Float = 1f

    /** UI 스레드에서 호출 — 다음 프레임([updateTexCoords])에 반영된다. */
    fun setZoom(factor: Float) {
        zoomFactor = factor.coerceIn(1f, 4f)
    }

    fun createOnGlThread() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        val target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(target, textureId)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_SRC)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SRC)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureUniform = GLES20.glGetUniformLocation(program, "sTexture")
    }

    /**
     * 매 프레임 화면 회전/크롭에 맞는 텍스처 좌표(base)를 필요할 때만 다시 계산하고,
     * 여기에 [zoomFactor]를 적용한 좌표로 [quadTexCoords]를 매 프레임 갱신한다.
     * 줌은 물리 카메라와 무관한 순수 디지털 크롭이라, 화면에 보이는 범위만 좁혀질 뿐
     * ARCore의 depth/추적 파이프라인([com.reodavin.ardistance.pipeline.FrameProcessor])에는
     * 아무 영향이 없다 — 좌표 변환은 UI 레이어([com.reodavin.ardistance.ui.BoxSelectionOverlay])에서만 처리한다.
     */
    fun updateTexCoords(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            val transformed = floatBufferOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quadCoords,
                Coordinates2d.TEXTURE_NORMALIZED,
                transformed,
            )
            baseTexCoords = transformed
        }
        quadTexCoords = applyZoom(baseTexCoords, zoomFactor)
    }

    /** [base]의 4개 텍스처 좌표를 그 중심 기준으로 1/zoom만큼 크롭해 확대 효과를 낸다. */
    private fun applyZoom(base: FloatBuffer, zoom: Float): FloatBuffer {
        if (zoom <= 1f) return base

        val values = FloatArray(8)
        base.position(0)
        base.get(values)
        base.position(0)

        var centerX = 0f
        var centerY = 0f
        for (i in 0 until 4) {
            centerX += values[i * 2]
            centerY += values[i * 2 + 1]
        }
        centerX /= 4f
        centerY /= 4f

        for (i in 0 until 4) {
            values[i * 2] = centerX + (values[i * 2] - centerX) / zoom
            values[i * 2 + 1] = centerY + (values[i * 2 + 1] - centerY) / zoom
        }
        return floatBufferOf(*values)
    }

    fun draw() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureUniform, 0)

        quadCoords.position(0)
        GLES20.glVertexAttribPointer(positionAttrib, 2, GLES20.GL_FLOAT, false, 0, quadCoords)
        quadTexCoords.position(0)
        GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)

        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glEnableVertexAttribArray(texCoordAttrib)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDisableVertexAttribArray(texCoordAttrib)

        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun floatBufferOf(vararg values: Float): FloatBuffer {
        return ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
    }

    private companion object {
        const val VERTEX_SHADER_SRC = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        const val FRAGMENT_SHADER_SRC = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, v_TexCoord);
            }
        """
    }
}
