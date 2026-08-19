package com.reodavin.ardistance.pipeline

enum class MeasurementMode {
    /** 박스 1개만 지정 — 카메라(사용자)로부터 해당 사물까지의 거리를 측정. */
    ONE_TARGET,

    /** 박스 2개를 지정 — 두 사물 사이의 거리를 측정 (기존 동작). */
    TWO_TARGET,
}
