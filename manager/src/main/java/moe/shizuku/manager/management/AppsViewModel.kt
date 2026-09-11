package moe.shizuku.manager.management

import android.content.Context
import android.content.pm.PackageInfo
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
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
import moe.shizuku.manager.authorization.AuthorizationManager
import rikka.lifecycle.Resource
import rikka.lifecycle.activityViewModels
import rikka.lifecycle.viewModels

@MainThread
fun ComponentActivity.appsViewModel() = viewModels { AppsViewModel(this) }

@MainThread
fun Fragment.appsViewModel() = activityViewModels { AppsViewModel(requireContext()) }

class AppsViewModel(context: Context) : ViewModel() {
    private var loadJob: Job? = null
    private val loadMutex = Mutex()

    private val _packages = MutableLiveData<Resource<List<PackageInfo>>>()
    val packages = _packages as LiveData<Resource<List<PackageInfo>>>

    private val _grantedCount = MutableLiveData<Resource<Int>>()
    val grantedCount = _grantedCount as LiveData<Resource<Int>>

    fun load(onlyCount: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                loadMutex.withLock {
                    val list: MutableList<PackageInfo> = ArrayList()
                    var count = 0
                    for (pi in AuthorizationManager.getPackages()) {
                        ensureActive()
                        val ai = pi.applicationInfo ?: continue
                        list.add(pi)
                        if (AuthorizationManager.granted(pi.packageName, ai.uid)) count++
                    }
                    ensureActive()
                    if (!onlyCount) _packages.postValue(Resource.success(list))
                    _grantedCount.postValue(Resource.success(count))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ensureActive()
                _packages.postValue(Resource.error(e, null))
                _grantedCount.postValue(Resource.error(e, 0))
            }
        }
    }
}
