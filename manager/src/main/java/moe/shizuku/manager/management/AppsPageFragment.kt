package moe.shizuku.manager.management

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.AppsActivityBinding
import rikka.lifecycle.Status
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.shizuku.Shizuku
import java.util.*
import android.graphics.Rect
import android.content.pm.PackageInfo
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable

class AppsPageFragment : Fragment() {

    private val viewModel by appsViewModel()
    private val adapter = AppsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = AppsActivityBinding.inflate(inflater, container, false)
        val recyclerView = binding.list
        recyclerView.fixEdgeEffect()
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null
        recyclerView.adapter = adapter
        recyclerView.addEdgeSpacing(
            left = 16f,
            right = 16f,
            top = 8f,
            bottom = 100f,
            unit = TypedValue.COMPLEX_UNIT_DIP
        )
        recyclerView.addItemDecoration(AppsCardDecoration())

        viewModel.packages.observe(viewLifecycleOwner) {
            when (it.status) {
                Status.SUCCESS -> adapter.updateData(it.data)
                Status.ERROR -> {
                    val message = Objects.toString(it.error, "unknown")
                    // Binder may not be ready right after launch; don't surface transient binder errors.
                    if (!message.contains("binder haven't been received", ignoreCase = true) &&
                        !message.contains("IllegalStateException", ignoreCase = true)
                    ) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
                Status.LOADING -> Unit
            }
        }
        if (viewModel.packages.value == null) {
            viewModel.load()
        }

        adapter.registerAdapterDataObserver(object : AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                if (Shizuku.pingBinder()) {
                    viewModel.load(true)
                }
            }
        })

        return binding.root
    }

    private inner class AppsCardDecoration : RecyclerView.ItemDecoration() {
        private val itemVertical = (1f * resources.displayMetrics.density).toInt()
        private val cornerRadius = 28f * resources.displayMetrics.density
        private val middleRadius = 2f * resources.displayMetrics.density
        private val cardColor = requireContext().getColor(R.color.home_card_background_color)
        private val rippleColor by lazy { resolveRippleColor() }

        override fun onDraw(c: android.graphics.Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val appsAdapter = parent.adapter as? AppsAdapter ?: return
            val items = appsAdapter.dataItems
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val pos = parent.getChildAdapterPosition(child)
                if (pos == RecyclerView.NO_POSITION) continue
                val item = items.getOrNull(pos)
                if (item !is PackageInfo) continue

                val prevIsCard = pos > 0 && items.getOrNull(pos - 1) is PackageInfo
                val nextIsCard = pos < items.size - 1 && items.getOrNull(pos + 1) is PackageInfo
                val topRadius = if (prevIsCard) middleRadius else cornerRadius
                val bottomRadius = if (nextIsCard) middleRadius else cornerRadius
                val shapeCode = when {
                    !prevIsCard && !nextIsCard -> 0
                    !prevIsCard -> 1
                    !nextIsCard -> 2
                    else -> 3
                }
                val needResetBackground =
                    (child.getTag(R.id.tag_card_shape_code) as? Int) != shapeCode ||
                        child.background !is RippleDrawable
                if (needResetBackground) {
                    child.setTag(R.id.tag_card_shape_code, shapeCode)
                    child.background = createCardBackground(topRadius, bottomRadius, rippleColor, cardColor)
                }
            }
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val pos = parent.getChildAdapterPosition(view)
            if (pos == RecyclerView.NO_POSITION) return
            val item = (parent.adapter as? AppsAdapter)?.dataItems?.getOrNull(pos)
            if (item is PackageInfo) {
                outRect.top = itemVertical
                outRect.bottom = itemVertical
            }
        }

        private fun resolveRippleColor(): Int {
            val out = android.util.TypedValue()
            requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorControlHighlight, out, true)
            return out.data
        }

        private fun createCardBackground(topRadius: Float, bottomRadius: Float, rippleColor: Int, cardColor: Int): RippleDrawable {
            val content = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    topRadius, topRadius,
                    topRadius, topRadius,
                    bottomRadius, bottomRadius,
                    bottomRadius, bottomRadius
                )
                setColor(cardColor)
            }
            return RippleDrawable(ColorStateList.valueOf(rippleColor), content, content.constantState?.newDrawable())
        }
    }
}
