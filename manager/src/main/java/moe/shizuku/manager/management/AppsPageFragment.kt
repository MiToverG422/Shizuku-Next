package moe.shizuku.manager.management
import moe.shizuku.manager.ui.component.pageNestedScroll

import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.Helps
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.ui.component.MaterialRow
import moe.shizuku.manager.ui.component.UiMetrics
import moe.shizuku.manager.ui.theme.ShizukuTheme
import moe.shizuku.manager.utils.AppIconCache
import moe.shizuku.manager.utils.ShizukuSystemApis
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.lifecycle.Status
import rikka.shizuku.Shizuku

class AppsPageFragment : Fragment() {
    private val viewModel by appsViewModel()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            filterTouchesWhenObscured = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { ShizukuTheme { AppsScreen() } }
        }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    @Composable
    private fun AppsScreen() {
        val resource by viewModel.packages.observeAsState()
        val packages = resource?.data.orEmpty()
        var error by remember { mutableStateOf<Pair<Int, String>?>(null) }
        LaunchedEffect(resource) {
            if (resource?.status == Status.ERROR && Shizuku.pingBinder()) {
                error = R.string.nav_apps to (resource?.error?.message ?: "Unknown error")
            }
        }
        val bottomPadding = moe.shizuku.manager.ui.component.LocalPageBottomPadding.current
        LazyColumn(Modifier.fillMaxSize().pageNestedScroll(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, bottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (resource == null || resource?.status == Status.LOADING) item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            if (resource?.status == Status.ERROR || (resource != null && packages.isEmpty())) item {
                MaterialRow(stringResource(R.string.nav_apps),
                    stringResource(if (!Shizuku.pingBinder()) R.string.app_management_empty_service_not_running
                        else R.string.home_app_management_empty), R.drawable.ic_apps_24dp)
            }
            itemsIndexed(packages, key = { _, pi -> "${pi.packageName}:${pi.applicationInfo?.uid}" }) { index, pi ->
                AppRow(pi, index, packages.size, onError = { error = it })
            }
        }
        if (error != null) AlertDialog(onDismissRequest = { error = null },
            title = { Text(stringResource(error!!.first)) },
            text = { Text(error!!.second) },
            confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(android.R.string.ok)) } })
    }

    @Composable
    private fun AppRow(pi: PackageInfo, index: Int, count: Int, onError: (Pair<Int, String>) -> Unit) {
        val context = LocalContext.current
        val ai = pi.applicationInfo ?: return
        val scope = rememberCoroutineScope()
        var busy by remember(pi) { mutableStateOf(false) }
        var granted by remember(pi) { mutableStateOf(false) }
        var loaded by remember(pi) { mutableStateOf(false) }
        var label by remember(pi) { mutableStateOf(pi.packageName) }
        var bitmap by remember(pi) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(pi) {
            val row = withContext(Dispatchers.IO) {
                val userId = UserHandleCompat.getUserId(ai.uid)
                val name = runCatching { ai.loadLabel(context.packageManager).toString() }.getOrDefault(pi.packageName)
                val displayName = if (userId == UserHandleCompat.myUserId()) name
                    else "$name - ${runCatching { ShizukuSystemApis.getUserInfo(userId).name }.getOrDefault(userId.toString())} ($userId)"
                Triple(displayName, runCatching { AuthorizationManager.granted(pi.packageName, ai.uid) }.getOrDefault(false),
                    runCatching { AppIconCache.getOrLoadBitmap(context, ai, userId,
                        (UiMetrics.AppIconSize.value * context.resources.displayMetrics.density).toInt()) }.getOrNull())
            }
            label = row.first
            granted = row.second
            bitmap = row.third
            loaded = true
        }
        val requiresRoot = ai.metaData?.getBoolean("moe.shizuku.client.V3_REQUIRES_ROOT") == true
        val limitedMessage = stringResource(R.string.app_management_dialog_adb_is_limited_message,
            Helps.ADB.get()).toHtml().toString()
        MaterialRow(title = label, summary = pi.packageName + if (requiresRoot)
            " · " + stringResource(R.string.app_management_item_summary_requires_root) else "",
            index = index, count = count, enabled = loaded && !busy, checked = granted,
            leading = {
                bitmap?.let { Image(it.asImageBitmap(), null, Modifier.size(UiMetrics.AppIconSize)) }
                    ?: Icon(painterResource(R.drawable.ic_apps_24dp), null, Modifier.size(UiMetrics.AppIconSize))
            },
            onCheckedChange = { desired ->
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            if (desired) AuthorizationManager.grant(pi.packageName, ai.uid)
                            else AuthorizationManager.revoke(pi.packageName, ai.uid)
                        }
                        granted = desired
                        // Shared UIDs may affect more than one row.
                        viewModel.load()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        if (e is SecurityException && runCatching { Shizuku.getUid() }.getOrDefault(-1) != 0) {
                            onError(R.string.app_management_dialog_adb_is_limited_title to limitedMessage)
                        } else onError(R.string.nav_apps to (e.message ?: e.javaClass.simpleName))
                    } finally { busy = false }
                }
            })
    }
}
