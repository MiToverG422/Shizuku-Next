package moe.shizuku.manager.home

import android.os.Build
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.recyclerview.IdBasedRecyclerViewAdapter
import rikka.recyclerview.IndexCreatorPool

class ActivationMethodsAdapter : IdBasedRecyclerViewAdapter(ArrayList()) {

    companion object {
        private const val ID_START_ROOT = 1L
        private const val ID_START_WADB = 2L
        private const val ID_START_ADB = 3L
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateCreatorPool(): IndexCreatorPool {
        return IndexCreatorPool()
    }

    fun updateData(status: ServiceStatus) {
        val running = status.isRunning
        val isPrimaryUser = UserHandleCompat.myUserId() == 0

        clear()
        if (isPrimaryUser) {
            val root = EnvironmentUtils.isRooted()
            val rootRestart = running && status.uid == 0

            if (root) {
                addItem(StartRootViewHolder.CREATOR, rootRestart, ID_START_ROOT)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || EnvironmentUtils.getAdbTcpPort() > 0) {
                addItem(StartWirelessAdbViewHolder.CREATOR, null, ID_START_WADB)
            }

            addItem(StartAdbViewHolder.CREATOR, null, ID_START_ADB)

            if (!root) {
                addItem(StartRootViewHolder.CREATOR, rootRestart, ID_START_ROOT)
            }
        }
        notifyDataSetChanged()
    }
}

