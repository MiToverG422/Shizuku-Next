package moe.shizuku.manager.home

import android.view.View
import android.view.ViewParent
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

internal object HomeCardShapeHelper {

    fun applyGroupedShape(view: View, firstCardAdapterPosition: Int = 1) {
        val card = view as? MaterialCardView ?: return
        val parent = findRecyclerViewParent(card.parent)
        if (parent == null) {
            // onBind can run before the view is attached; retry once on next frame.
            card.post { applyGroupedShape(card, firstCardAdapterPosition) }
            return
        }
        val position = parent.getChildAdapterPosition(card)
        val total = parent.adapter?.itemCount ?: return
        if (position == RecyclerView.NO_POSITION || total <= 0) return

        val isFirst = position <= firstCardAdapterPosition
        val isLast = position == total - 1

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
}
