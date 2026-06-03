package moe.shizuku.manager.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import moe.shizuku.manager.R
import rikka.core.ktx.unsafeLazy

abstract class AppBarActivity : AppActivity() {

    private val rootView: ViewGroup by unsafeLazy {
        findViewById<ViewGroup>(R.id.root)
    }

    private val toolbarContainer: AppBarLayout by unsafeLazy {
        findViewById<AppBarLayout>(R.id.toolbar_container)
    }

    private val contentContainer: ViewGroup by unsafeLazy {
        findViewById<ViewGroup?>(R.id.content_container) ?: rootView
    }

    private val toolbar: Toolbar by unsafeLazy {
        findViewById<Toolbar>(R.id.toolbar)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.setContentView(getLayoutId())
        configureContentContainer()

        setSupportActionBar(toolbar)
        setAppBarTitle(title)
    }

    @LayoutRes
    open fun getLayoutId(): Int {
        return R.layout.appbar_activity
    }

    protected open fun useAppBarScrollingContent(): Boolean {
        return true
    }

    override fun setContentView(layoutResID: Int) {
        setContentView(layoutInflater.inflate(layoutResID, rootView, false))
    }

    override fun setContentView(view: View?) {
        setContentView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        if (view == null) return
        contentContainer.addView(view, createContentLayoutParams(params))
        rootView.bringChildToFront(toolbarContainer)
    }

    protected fun setAppBarTitle(title: CharSequence?) {
        toolbar.title = title
        supportActionBar?.title = title
    }

    private fun configureContentContainer() {
        val params = contentContainer.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        params.behavior = if (useAppBarScrollingContent()) {
            AppBarLayout.ScrollingViewBehavior()
        } else {
            null
        }
        contentContainer.layoutParams = params
    }

    private fun createContentLayoutParams(params: ViewGroup.LayoutParams?): ViewGroup.LayoutParams {
        val width = params?.width ?: ViewGroup.LayoutParams.MATCH_PARENT
        val height = params?.height ?: ViewGroup.LayoutParams.MATCH_PARENT
        if (contentContainer is CoordinatorLayout) {
            return CoordinatorLayout.LayoutParams(width, height).apply {
                if (params is ViewGroup.MarginLayoutParams) {
                    setMargins(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin)
                }
                behavior = AppBarLayout.ScrollingViewBehavior()
            }
        }
        return FrameLayout.LayoutParams(width, height).apply {
            if (params is ViewGroup.MarginLayoutParams) {
                setMargins(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onApplyTranslucentSystemBars() {
        super.onApplyTranslucentSystemBars()
        window?.statusBarColor = Color.TRANSPARENT
    }
}

abstract class AppBarFragmentActivity : AppBarActivity() {

    override fun getLayoutId(): Int {
        return R.layout.appbar_fragment_activity
    }
}
