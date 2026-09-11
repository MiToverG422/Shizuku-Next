package moe.shizuku.manager.ui.component

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import moe.shizuku.manager.ui.theme.ShizukuTheme

/** Fragment-owned Compose dialog; one dialog window, with restored fragment state. */
abstract class MaterialDialogFragment : Fragment() {
    @Composable protected abstract fun Content()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { ShizukuTheme { this@MaterialDialogFragment.Content() } }
        }

    fun show(fragmentManager: FragmentManager) {
        if (fragmentManager.isStateSaved || fragmentManager.findFragmentByTag(javaClass.name) != null) return
        fragmentManager.beginTransaction().add(android.R.id.content, this, javaClass.name).commit()
    }

    protected fun dismissAllowingStateLoss() {
        parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }
}
