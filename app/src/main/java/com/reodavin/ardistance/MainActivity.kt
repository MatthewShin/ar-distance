package com.reodavin.ardistance

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.reodavin.ardistance.ar.ArRenderer
import com.reodavin.ardistance.ar.ArSessionManager
import com.reodavin.ardistance.ar.DepthSupport
import com.reodavin.ardistance.pipeline.FrameProcessor
import com.reodavin.ardistance.pipeline.MeasurementMode
import com.reodavin.ardistance.pipeline.MeasurementState
import com.reodavin.ardistance.ui.BoxSelectionOverlay
import com.reodavin.ardistance.ui.MeasurementViewModel

class MainActivity : ComponentActivity() {

    private lateinit var arSessionManager: ArSessionManager
    private var glSurfaceView: GLSurfaceView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var cameraPermissionGranted by mutableStateOf(false)
    private var statusText by mutableStateOf("초기화 중…")

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraPermissionGranted = granted
            if (!granted) {
                statusText = "카메라 권한이 필요합니다"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arSessionManager = ArSessionManager(this)

        cameraPermissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        if (!cameraPermissionGranted) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (cameraPermissionGranted) {
                        ArCameraScreen(status = statusText)
                    } else {
                        PermissionRequestScreen(
                            status = statusText,
                            onRequestPermission = { requestCameraPermission.launch(Manifest.permission.CAMERA) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ArCameraScreen(status: String) {
        val measurementViewModel: MeasurementViewModel = viewModel()
        val measurementState by measurementViewModel.state.collectAsState()

        val frameProcessor = remember {
            FrameProcessor { result ->
                mainHandler.post { measurementViewModel.updateFrameResult(result) }
            }
        }

        val context = LocalContext.current
        val vibrator = remember { context.getVibratorCompat() }
        val distanceMeters = (measurementState as? MeasurementState.Tracking)?.distanceMeters
        val isProximityWarning = distanceMeters != null && distanceMeters < PROXIMITY_WARNING_METERS

        // 임계값 아래로 "새로 진입"할 때만 1회 진동 (계속 붙어있는 동안 반복 진동하지 않도록).
        var wasWarning by remember { mutableStateOf(false) }
        LaunchedEffect(isProximityWarning) {
            if (isProximityWarning && !wasWarning) {
                vibrator.vibrateOnce()
            }
            wasWarning = isProximityWarning
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { glContext ->
                    GLSurfaceView(glContext).apply {
                        setEGLContextClientVersion(2)
                        setRenderer(
                            ArRenderer(arSessionManager, frameProcessor) { frameStatus ->
                                mainHandler.post { statusText = frameStatus.toDisplayText() }
                            }
                        )
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        glSurfaceView = this
                    }
                },
            )

            BoxSelectionOverlay(
                state = measurementState,
                onBoxConfirmed = { rect ->
                    val index = measurementViewModel.confirmBox(rect)
                    if (index != null) {
                        frameProcessor.requestInit(index, rect)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (measurementState is MeasurementState.SelectingMode) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(24.dp),
                ) {
                    Text("측정 모드를 선택하세요", color = Color.White, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        measurementViewModel.selectMode(MeasurementMode.ONE_TARGET)
                        frameProcessor.setMode(MeasurementMode.ONE_TARGET)
                    }) {
                        Text("1타겟 모드 (카메라→사물 거리)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        measurementViewModel.selectMode(MeasurementMode.TWO_TARGET)
                        frameProcessor.setMode(MeasurementMode.TWO_TARGET)
                    }) {
                        Text("2타겟 모드 (사물↔사물 거리)")
                    }
                }
            }

            Text(
                text = status,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp),
            )

            // 거리 텍스트는 이제 BoxSelectionOverlay가 실제 추적 위치(박스 사이 또는
            // 화면 하단→박스)에 선과 함께 그린다 — 화면 정중앙 고정 표시는 제거 (TODO §3D 렌더링).

            if (measurementState !is MeasurementState.SelectingMode) {
                Text(
                    text = measurementState.guideText(),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(8.dp),
                )
            }

            Button(
                onClick = {
                    measurementViewModel.reset()
                    frameProcessor.resetAll()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            ) {
                Text("초기화")
            }

            if (isProximityWarning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(width = 12.dp, color = Color.Red),
                )
                Text(
                    text = "근접 경고: %.2f m".format(distanceMeters),
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .background(Color.Red.copy(alpha = 0.8f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!cameraPermissionGranted) return

        if (arSessionManager.tryCreateSession()) {
            statusText = when (arSessionManager.depthSupport) {
                DepthSupport.Supported -> "ARCore 세션 시작됨 (Depth API 지원)"
                DepthSupport.Unsupported -> "ARCore 세션 시작됨 (Depth API 미지원 — 이 기기는 거리 측정 불가)"
            }
            arSessionManager.resume()
            glSurfaceView?.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView?.onPause()
        arSessionManager.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arSessionManager.close()
    }
}

/** 거리가 이 값(미터) 미만이면 근접 경고를 표시한다. TODO: 사용자가 조절 가능하게 만들 수 있음. */
private const val PROXIMITY_WARNING_METERS = 0.3f

private fun Context.getVibratorCompat(): Vibrator {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
}

private fun Vibrator.vibrateOnce() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrate(200)
    }
}

private fun MeasurementState.guideText(): String {
    return when (this) {
        is MeasurementState.SelectingMode -> "" // 모드 선택 화면에서는 별도 UI로 안내
        is MeasurementState.SelectingBox1 -> {
            if (mode == MeasurementMode.ONE_TARGET) {
                "박스(빨강)를 드래그하여 거리를 잴 사물을 지정하세요"
            } else {
                "박스1(빨강)을 드래그하여 첫 번째 사물을 지정하세요"
            }
        }
        is MeasurementState.SelectingBox2 -> "박스2(파랑)를 드래그하여 두 번째 사물을 지정하세요"
        is MeasurementState.Tracking -> {
            val box1Text = if (box1Lost) "놓침" else "추적 중"
            if (mode == MeasurementMode.ONE_TARGET) {
                "타겟: $box1Text"
            } else {
                val box2Text = if (box2Lost) "놓침" else "추적 중"
                "박스1: $box1Text / 박스2: $box2Text"
            }
        }
    }
}

private fun ArRenderer.FrameStatus.toDisplayText(): String {
    return when (trackingState) {
        TrackingState.TRACKING -> "추적 중 (TRACKING)"
        TrackingState.PAUSED -> "추적 불안정: ${failureReasonText(failureReason)}"
        TrackingState.STOPPED -> "세션 중지됨"
    }
}

private fun failureReasonText(reason: TrackingFailureReason): String {
    return when (reason) {
        TrackingFailureReason.NONE -> "알 수 없음"
        TrackingFailureReason.BAD_STATE -> "내부 오류"
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "조명이 너무 어두움"
        TrackingFailureReason.EXCESSIVE_MOTION -> "카메라를 너무 빠르게 움직임"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "특징점이 부족한 환경 (단색 벽 등)"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "카메라 사용 불가"
        else -> "알 수 없음"
    }
}

@Composable
private fun PermissionRequestScreen(status: String, onRequestPermission: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(status)
            Button(onClick = onRequestPermission) {
                Text("카메라 권한 요청")
            }
        }
    }
}
