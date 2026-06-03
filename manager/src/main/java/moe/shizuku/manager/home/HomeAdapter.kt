package moe.shizuku.manager.home

import rikka.recyclerview.IdBasedRecyclerViewAdapter
import rikka.recyclerview.IndexCreatorPool

class HomeAdapter(
    private val homeModel: HomeViewModel,
    private val onAuthorizedCountClick: () -> Unit,
    private val onStopClick: () -> Unit
) :
    IdBasedRecyclerViewAdapter(ArrayList()) {

    init {
        updateData()
        setHasStableIds(true)
    }

    companion object {

        internal const val ID_STATUS = 0L
        internal const val ID_VERSION_INFO = 1L
        internal const val ID_TERMINAL = 2L
        internal const val ID_LEARN_MORE = 6L
        internal const val ID_ADB_PERMISSION_LIMITED = 7L
        internal const val ID_AUTHORIZED_COUNT = 8L
    }

    var grantedCount: Int = 0
        set(value) {
            field = value
            updateData()
        }

    override fun onCreateCreatorPool(): IndexCreatorPool {
        return IndexCreatorPool()
    }

    fun updateData() {
        val status = homeModel.serviceStatus.value?.data ?: return
        val adbPermission = status.permission
        val running = status.isRunning

        clear()
        addItem(ServerStatusViewHolder.CREATOR, status, ID_STATUS)
        if (running) {
            addItem(
                AuthorizedCountViewHolder.CREATOR,
                AuthorizedCountViewHolder.Data(grantedCount, onAuthorizedCountClick, onStopClick),
                ID_AUTHORIZED_COUNT
            )
        }
        addItem(VersionInfoViewHolder.CREATOR, null, ID_VERSION_INFO)

        if (adbPermission) {
            addItem(TerminalViewHolder.CREATOR, status, ID_TERMINAL)
        }

        if (running && !adbPermission) {
            addItem(AdbPermissionLimitedViewHolder.CREATOR, status, ID_ADB_PERMISSION_LIMITED)
        }

        addItem(LearnMoreViewHolder.CREATOR, null, ID_LEARN_MORE)
        notifyDataSetChanged()
    }
}
