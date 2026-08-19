package com.reodavin.ardistance.tracking

import android.util.Log
import org.opencv.android.OpenCVLoader

/** OpenCV 네이티브 라이브러리를 앱 전체에서 한 번만 초기화한다. */
object OpenCvLoader {
    private const val TAG = "OpenCvLoader"
    private var initialized = false

    @Synchronized
    fun ensureInitialized(): Boolean {
        if (initialized) return true
        initialized = try {
            OpenCVLoader.initLocal()
        } catch (e: Throwable) {
            Log.e(TAG, "OpenCV 초기화 실패", e)
            false
        }
        if (!initialized) {
            Log.e(TAG, "OpenCVLoader.initLocal()이 false를 반환했습니다")
        }
        return initialized
    }
}
