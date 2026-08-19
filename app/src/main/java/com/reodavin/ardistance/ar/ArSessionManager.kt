package com.reodavin.ardistance.ar

import android.app.Activity
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException

/**
 * ARCore [Session]의 생명주기(생성/설치유도/resume/pause/close)를 담당한다.
 * Activity의 onResume/onPause와 짝을 맞춰 호출해야 한다 (ARCore 공식 권장 패턴).
 */
class ArSessionManager(private val activity: Activity) {

    var session: Session? = null
        private set

    var depthSupport: DepthSupport = DepthSupport.Unsupported
        private set

    private var installRequested = false

    /**
     * onResume에서 호출한다. ARCore 미설치 시 설치를 요청하고 false를 반환하는데,
     * 이 경우 이번 resume 사이클에서는 세션을 만들지 않고 설치 완료 후 액티비티가
     * 다시 resume될 때 재시도한다 (ARCore 공식 샘플과 동일한 패턴).
     */
    fun tryCreateSession(): Boolean {
        if (session != null) return true

        try {
            val installStatus = ArCoreApk.getInstance().requestInstall(activity, !installRequested)
            if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true
                return false
            }

            val newSession = Session(activity)
            configure(newSession)
            depthSupport = DepthSupport.check(newSession)
            session = newSession
            return true
        } catch (e: UnavailableArcoreNotInstalledException) {
            logUnavailable("ARCore가 설치되어 있지 않습니다", e)
        } catch (e: UnavailableUserDeclinedInstallationException) {
            logUnavailable("사용자가 ARCore 설치를 거부했습니다", e)
        } catch (e: UnavailableApkTooOldException) {
            logUnavailable("ARCore 버전이 오래되었습니다. 업데이트가 필요합니다", e)
        } catch (e: UnavailableSdkTooOldException) {
            logUnavailable("앱의 ARCore SDK 버전이 오래되었습니다", e)
        } catch (e: UnavailableDeviceNotCompatibleException) {
            logUnavailable("이 기기는 ARCore를 지원하지 않습니다", e)
        } catch (e: Exception) {
            logUnavailable("ARCore 세션 생성에 실패했습니다", e)
        }
        return false
    }

    private fun configure(session: Session) {
        val config = Config(session)
        config.focusMode = Config.FocusMode.AUTO
        // 사물 지정은 사용자가 직접 박스로 하므로 평면 인식은 불필요 (연산 절약).
        config.planeFindingMode = Config.PlaneFindingMode.DISABLED
        // 프레임 드롭을 허용해 지연을 최소화 (계획 §4.2).
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.depthMode = Config.DepthMode.AUTOMATIC
        }
        session.configure(config)
    }

    fun resume() {
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "카메라를 사용할 수 없습니다 (다른 앱이 사용 중일 수 있음)", e)
            session = null
        }
    }

    fun pause() {
        session?.pause()
    }

    fun close() {
        session?.close()
        session = null
    }

    private fun logUnavailable(message: String, e: Exception) {
        Log.e(TAG, message, e)
    }

    companion object {
        private const val TAG = "ArSessionManager"
    }
}
