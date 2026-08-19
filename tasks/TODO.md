# TODO

v1.0.0(1차 테스트) 완료 후 제안된 개선 항목. 진행 전 [AGENTS.md](../AGENTS.md)의 작업 시작 규칙 참고.

- [ ] **depth 노이즈 완화**: 지금은 박스 중심 픽셀 1개만 읽어 depth를 조회함(`FrameProcessor.sampleDepthMeters`). 3x3 median 샘플링 등으로 노이즈 완화 필요. (계획 문서 §6, `/Users/reodavin/.claude/plans/1-calm-thimble.md` 참고)
- [ ] **실제 3D 공간에 거리선/텍스트 렌더링**: 지금은 2D Compose Canvas 오버레이라 화면 각도가 바뀌면 사물에서 어긋나 보임. ARCore 앵커 기반으로 실제 3D 공간에 붙어있는 자처럼 라인/텍스트를 렌더링하면 AR 체감이 개선됨.
- [ ] **근접 경고**: 두 타겟(또는 카메라-타겟) 거리가 특정 임계값 이하로 좁혀지면 화면 경고 표시/진동 등으로 알림. 안전거리 감시 같은 실사용 시나리오에 바로 연결됨.
