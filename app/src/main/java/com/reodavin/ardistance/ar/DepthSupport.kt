package com.reodavin.ardistance.ar

import com.google.ar.core.Config
import com.google.ar.core.Session

/**
 * ARCore Depth API 지원 여부를 캡슐화한다.
 *
 * §7 (계획): 미지원 기기에서는 M4 이후 실측 거리 계산 기능을 비활성화하고
 * 안내만 표시한다. 평면 히트테스트 폴백은 "임의 사물" 요구사항과
 * 맞지 않아 구현 범위에서 제외했다 (계획 문서 §7 참고).
 */
sealed class DepthSupport {
    data object Supported : DepthSupport()
    data object Unsupported : DepthSupport()

    companion object {
        fun check(session: Session): DepthSupport {
            return if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                Supported
            } else {
                Unsupported
            }
        }
    }
}
