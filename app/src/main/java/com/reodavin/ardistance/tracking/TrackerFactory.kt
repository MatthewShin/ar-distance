package com.reodavin.ardistance.tracking

/**
 * TrackerCSRT/KCF(opencv_contrib)는 Maven Central의 core AAR(org.opencv:opencv)에
 * Java 바인딩이 노출되는지 컴파일 시점에 확신할 수 없어(계획 §2.3), 컴파일 안정성을 위해
 * 처음부터 core 모듈만으로 구현한 [OpticalFlowBoxTracker]를 사용한다.
 */
object TrackerFactory {
    fun create(): BoxTracker = OpticalFlowBoxTracker()
}
