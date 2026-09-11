package moe.shizuku.manager.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.shizuku.manager.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            MaterialTopAppBar(title = title, scrollBehavior = scroll,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back_24), stringResource(androidx.appcompat.R.string.abc_action_bar_up_description))
                    }
                })
        }
    ) { padding ->
        CompositionLocalProvider(LocalPageScrollConnection provides scroll.nestedScrollConnection) {
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
        }
    }
}
