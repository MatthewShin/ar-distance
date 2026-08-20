# RELEASE.md

작업을 완료할 때마다 날짜와 간단한 내용을 여기에 기록한다. 최신 항목이 위로 오도록 추가한다.
[tasks/TODO.md](../tasks/TODO.md) 항목을 완료한 경우 해당 항목을 TODO.md에서 제거하고 여기에 기록한다 (규칙: [AGENTS.md](../AGENTS.md)).

## 2026-08-20 — UI 디자인 고급화 + 고성능 모드 UX 단순화

- `material-icons-extended` 아이콘, 앱 전용 다크 테마(`ArDistanceTheme`), 글래스 패널(`Modifier.glassPanel`), `AnimatedVisibility`/`LinearProgressIndicator` 애니메이션 적용 (실기기 확인 완료). Haze 블러 라이브러리는 Kotlin/Compose 버전 호환성 리스크로 제외
- 고성능 모드 체크박스 제거 — 타겟 추적 중이면 별도 모드 선택 없이 "정밀 측정" 버튼이 항상 노출되도록 단순화

## 2026-08-20 — 고성능 모드 (다중 시점 삼각측량)

- depth 센서 없이, 폰을 살짝 움직이며 모은 카메라 pose + 타겟 픽셀 위치(광선)만으로 3D 위치를 삼각측량하는 스냅샷 정밀측정 모드 추가. `geometry/RayTriangulator`(3x3 최소자승), `FrameProcessor.startPrecisionCapture()`(baseline 0.15m 도달 또는 4초 타임아웃 시 자동 확정)로 구현. 시작 화면에 "고성능 모드" 체크박스 추가, 트래킹 화면에 "정밀 측정" 버튼 + 진행률/결과/실패 안내 UI 추가. 기존 depth 기반 실시간 계산과 분리된 병행 경로라 일반 모드 동작에는 영향 없음 (실기기 확인 완료)

## 2026-08-20 — depth 다중 포인트 샘플링 (정확도 개선)

- `FrameProcessor.sampleDepthMeters`를 박스 중심 3x3 샘플링에서, 박스 내부(가장자리 15% 인셋) 5x5=25 포인트 격자 샘플링 + trimmed median(상하위 20% 제거)으로 확장. 단일 지점 노이즈/이상치에 더 강건해짐 (실기기 확인 완료)

## 2026-08-20 — 디지털 줌 기능

- `CameraBackgroundRenderer`에 화면 중심 기준 텍스처 크롭으로 1x~4x 디지털 줌 렌더링 추가, 좌하단 －/＋ 버튼으로 조작. `BoxSelectionOverlay`가 화면(줌)↔논리(줌 없음) 좌표를 양방향 변환해 `FrameProcessor`의 추적/거리 계산 로직은 줌과 무관하게 그대로 유지 (실기기 확인 완료, 배율 무관하게 거리값 동일함 검증)

## 2026-08-19 — 근접 경고

- 거리가 30cm 미만이면 화면 빨간 테두리 + 상단 경고 배지 표시, 임계값 진입 시 1회 진동. `VIBRATE` 권한 추가 (실기기 확인 완료)

## 2026-08-19 — 추적 위치 기반 거리선/텍스트 렌더링

- 화면 정중앙 고정 거리 텍스트를 제거하고, `BoxSelectionOverlay`에서 박스1↔박스2(2타겟) 또는 화면 하단→박스(1타겟) 사이에 선 + 거리 라벨을 실제 추적 좌표에 맞춰 그리도록 변경 (실기기 확인 완료). 진짜 3D OpenGL 앵커 렌더링 대신 기존 2D 추적 좌표를 활용하는 경량 방식으로 구현

## 2026-08-19 — depth 노이즈 완화

- `FrameProcessor.sampleDepthMeters`가 박스 중심 픽셀 1개 대신 3x3 이웃의 유효 depth 값 중 median을 사용하도록 변경 (이상치에 강함, 실기기 확인 완료)

## 2026-08-19 — v1.0.0 (1차 테스트)

- 카메라 프리뷰 + ARCore 세션
- 박스 드래그 UI (1타겟 모드 / 2타겟 모드)
- OpenCV optical flow 기반 실시간 박스 추적
- ARCore Depth API 기반 실측 거리 계산 (EMA 스무딩)
