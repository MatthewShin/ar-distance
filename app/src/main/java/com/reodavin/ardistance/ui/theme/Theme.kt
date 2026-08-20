package com.reodavin.ardistance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 앱 전용 다크 테마. AR 카메라 화면 위에 오버레이되는 UI라 항상 어두운 배경을 전제로 하며,
 * [AccentColor]를 포인트 컬러로 쓴다. [com.reodavin.ardistance.MainActivity.onCreate]에서
 * 기본 `MaterialTheme` 대신 이 래퍼를 사용한다.
 */
private val ArDistanceColorScheme = darkColorScheme(
    primary = AccentColor,
    onPrimary = Color.Black,
    secondary = AccentColor,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    error = WarningColor,
)

@Composable
fun ArDistanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArDistanceColorScheme,
        typography = Typography(),
        content = content,
    )
}
