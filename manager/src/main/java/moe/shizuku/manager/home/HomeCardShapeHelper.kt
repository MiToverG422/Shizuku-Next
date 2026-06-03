package moe.shizuku.manager.home

import android.view.View
import android.view.ViewParent
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

internal object HomeCardShapeHelper {

    fun applyStandaloneShape(view: View) {
        val card = view as? MaterialCardView ?: return
        val big = 28f * card.resources.displayMetrics.density

        card.shapeAppearanceModel = card.shapeAppearanceModel.toBuilder()
            .setTopLeftCornerSize(big)
            .setTopRightCornerSize(big)
            .setBottomLeftCornerSize(big)
            .setBottomRightCornerSize(big)
            .build()
    }

    fun applyGroupedShape(view: View, firstCardAdapterPosition: Int = 1) {
        val card = view as? MaterialCardView ?: return
        val parent = findRecyclerViewParent(card.parent)
        if (parent == null) {
            // onBind can run before the view is attached; retry once on next frame.
            card.post { applyGroupedShape(card, firstCardAdapterPosition) }
            return
        }
        val position = parent.getChildAdapterPosition(card)
        val adapter = parent.adapter ?: return
        val total = adapter.itemCount
        if (position == RecyclerView.NO_POSITION || total <= 0) return

        val (isFirst, isLast) = if (adapter is HomeAdapter && adapter.getItemId(position) in linkCardIds) {
            val first = findGroupEdge(adapter, position, -1)
            val last = findGroupEdge(adapter, position, 1)
            (position == first) to (position == last)
        } else {
            (position <= firstCardAdapterPosition) to (position == total - 1)
        }

        val big = 28f * card.resources.displayMetrics.density
        val small = 2f * card.resources.displayMetrics.density

        val top = if (isFirst) big else small
        val bottom = if (isLast) big else small

        card.shapeAppearanceModel = card.shapeAppearanceModel.toBuilder()
            .setTopLeftCornerSize(top)
            .setTopRightCornerSize(top)
            .setBottomLeftCornerSize(bottom)
            .setBottomRightCornerSize(bottom)
            .build()
    }

    private fun findRecyclerViewParent(parent: ViewParent?): RecyclerView? {
        var current = parent
        while (current != null) {
            if (current is RecyclerView) return current
            current = current.parent
        }
        return null
    }

    private fun findGroupEdge(adapter: RecyclerView.Adapter<*>, position: Int, direction: Int): Int {
        var edge = position
        while (true) {
            val next = edge + direction
            if (next !in 0 until adapter.itemCount || adapter.getItemId(next) !in linkCardIds) {
                return edge
            }
            edge = next
        }
    }

    private val linkCardIds = setOf(
        HomeAdapter.ID_TERMINAL,
        HomeAdapter.ID_LEARN_MORE,
        HomeAdapter.ID_ADB_PERMISSION_LIMITED
    )
}
