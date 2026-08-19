# AR Distance (1차 테스트 / PoC)

카메라로 지정한 두 사물(형태 인식 없이 박스로만 지정)을 실시간으로 추적하며, 두 사물 사이의 실제 거리(미터 단위)를 화면에 표시하는 안드로이드 AR 앱.

상세 설계는 `/Users/reodavin/.claude/plans/1-calm-thimble.md` 참고.

## 현재 상태 (2026-08-19 기준, 1차 테스트 종료)

실기기(Samsung SM-S938N)에서 아래 항목까지 빌드/실행 검증 완료:

- **M0**: Gradle 프로젝트 스캐폴딩, Compose/ARCore/OpenCV 의존성, 매니페스트 권한 구성
- **M1**: 카메라 프리뷰 + ARCore 세션(트래킹 상태 표시)
- **M2**: 박스 드래그 UI (박스1/박스2 순서 지정, 초기화)
- **M3**: OpenCV Optical Flow 기반 박스 실시간 추적 (사물이 움직여도 박스가 따라감)
  - `TrackerCSRT`/`TrackerKCF`(contrib)는 Maven Central AAR에 없어 처음부터 자체 optical flow 트래커로 구현
  - `Imgproc.goodFeaturesToTrack`도 이 OpenCV 빌드에 없어 균등 격자점 샘플링으로 대체
- **M4**: ARCore Depth API로 실측 거리(미터) 계산, EMA 스무딩
- **모드 확장**: 1타겟 모드(카메라→사물 거리) / 2타겟 모드(사물↔사물 거리) 선택 기능 추가

## 다음 단계 (미착수, 필요 시 진행)

- 정확도 검증: 줄자 실측값과 비교, 계획 목표 오차 ±10% 확인
- 성능 폴리싱: 현재 CV 연산이 GL 렌더링과 같은 스레드에서 동기 실행됨 — 별도 스레드 분리 필요할 수 있음
- depth 3x3 median 샘플링 등 노이즈 완화 (계획 §6)

## 빌드/실행 전제 조건

이 저장소는 로컬 셸에 JDK/Android SDK가 없는 환경에서 스캐폴딩되었습니다. **Android Studio(최신 버전)로 프로젝트를 열면 Gradle Wrapper와 SDK를 자동으로 구성**합니다.

- ARCore Depth API 및 OpenCV 네이티브 트래커는 에뮬레이터에서 정확도를 보장할 수 없으므로, **ARCore Depth API 지원 실기기**(Pixel 계열 등)로 테스트해야 합니다.
- 지원 기기 목록: https://developers.google.com/ar/devices

## 패키지 구조

```
app/src/main/java/com/reodavin/ardistance/
  MainActivity.kt
  ar/         // ARCore 세션 관리, 카메라 렌더러
  tracking/   // OpenCV 기반 박스 추적
  geometry/   // 픽셀+depth → 3D 좌표 변환, 거리 계산
  pipeline/   // 프레임 단위 처리 파이프라인
  ui/         // Compose UI (박스 드래그, 오버레이)
```
