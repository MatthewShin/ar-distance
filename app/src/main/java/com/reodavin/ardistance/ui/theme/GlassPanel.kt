package com.reodavin.ardistance.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * AR 카메라 화면 위 오버레이 패널에 공통으로 쓰는 "글래스" 스타일 — 둥근 모서리 +
 * 어두운 반투명 배경 + 옅은 테두리. 실제 배경 블러 없이도 기존 단색 반투명 배경보다
 * 정돈된 느낌을 준다 (Haze 등 블러 라이브러리는 현재 Compose/Kotlin 버전과 호환성 리스크가 있어 제외).
 */
fun Modifier.glassPanel(shape: Shape = RoundedCornerShape(14.dp)): Modifier {
    return this
        .clip(shape)
        .background(GlassBackground)
        .border(width = 1.dp, color = GlassBorder, shape = shape)
}
