package com.reodavin.ardistance.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 앱 전역에서 쓰는 시맨틱 색상 모음 — 여러 파일에 흩어져 있던 값을 한곳에 모았다.
 * 값 자체는 기존과 동일하게 유지해 화면 요소(박스/거리선/경고 등)의 시각적 의미가 바뀌지 않게 한다.
 */

/** 박스1(1타겟 모드에서는 유일한 박스) 색상 — 빨강. */
val Box1Color = Color(0xFFFF5252)

/** 박스2(2타겟 모드 전용) 색상 — 파랑. */
val Box2Color = Color(0xFF448AFF)

/** 추적 위치 사이에 그리는 거리선/라벨 색상 — 노랑. */
val DistanceLineColor = Color(0xFFFFEB3B)

/** 신뢰도 낮음 / 근접 경고 등 경고성 표시 색상. */
val WarningColor = Color(0xFFFF5252)

/** 다중 시점 삼각측량 정밀측정 결과 색상 — 초록. */
val PrecisionColor = Color(0xFF69F0AE)

/** 테마 포인트 컬러(버튼 등 강조 요소). */
val AccentColor = Color(0xFF64B5F6)

/** 글래스 패널 배경 — 어두운 반투명. */
val GlassBackground = Color(0xFF0A0A0A).copy(alpha = 0.62f)

/** 글래스 패널 테두리 — 옅은 흰색 반투명. */
val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.14f)
