package moe.shizuku.manager.home

import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.Manifest
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.Logger.LOGGER
import moe.shizuku.manager.utils.ShizukuSystemApis
import rikka.lifecycle.Resource
import rikka.shizuku.Shizuku

class HomeViewModel : ViewModel() {
    private var reloadJob: Job? = null
    private val reloadMutex = Mutex()

    private val _serviceStatus = MutableLiveData<Resource<ServiceStatus>>()
    val serviceStatus = _serviceStatus as LiveData<Resource<ServiceStatus>>

    private fun load(): ServiceStatus {
        if (!Shizuku.pingBinder()) {
            return ServiceStatus()
        }

        val uid = try {
            Shizuku.getUid()
        } catch (_: IllegalStateException) {
            return ServiceStatus()
        }
        val apiVersion = try {
            Shizuku.getVersion()
        } catch (_: IllegalStateException) {
            return ServiceStatus()
        }
        val patchVersion = try {
            Shizuku.getServerPatchVersion().let { if (it < 0) 0 else it }
        } catch (_: IllegalStateException) {
            0
        }
        val seContext = if (apiVersion >= 6) {
            try {
                Shizuku.getSELinuxContext()
            } catch (tr: Throwable) {
                LOGGER.w(tr, "getSELinuxContext")
                null
            }
        } else null
        val permissionTest = try {
            Shizuku.checkRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS") == PackageManager.PERMISSION_GRANTED
        } catch (tr: Throwable) {
            LOGGER.w(tr, "checkRemotePermission")
            false
        }

        // Before a526d6bb, server will not exit on uninstall, manager installed later will get not permission
        // Run a random remote transaction here, report no permission as not running
        try {
            ShizukuSystemApis.checkPermission(Manifest.permission.API_V23, BuildConfig.APPLICATION_ID, 0)
        } catch (tr: Throwable) {
            LOGGER.w(tr, "checkPermission")
            return ServiceStatus()
        }
        return ServiceStatus(uid, apiVersion, patchVersion, seContext, permissionTest)
    }

    fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = reloadMutex.withLock { load() }
                ensureActive()
                _serviceStatus.postValue(Resource.success(status))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ensureActive()
                _serviceStatus.postValue(Resource.error(e, ServiceStatus()))
            }
        }
    }
}
