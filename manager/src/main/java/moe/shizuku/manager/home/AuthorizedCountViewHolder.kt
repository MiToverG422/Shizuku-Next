package moe.shizuku.manager.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.HomeAuthorizedCountBinding
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

class AuthorizedCountViewHolder(private val binding: HomeAuthorizedCountBinding, root: View) :
    BaseViewHolder<AuthorizedCountViewHolder.Data>(root) {

    data class Data(
        val count: Int,
        val onClick: () -> Unit,
        val onStopClick: () -> Unit
    )

    companion object {
        val CREATOR = Creator<Data> { inflater: LayoutInflater, parent: ViewGroup? ->
            val binding = HomeAuthorizedCountBinding.inflate(inflater, parent, false)
            AuthorizedCountViewHolder(binding, binding.root)
        }
    }

    override fun onBind() {
        applyStandaloneMargins()
        binding.authorizedCount.text = data.count.toString()
        binding.authorizedCard.setOnClickListener {
            data.onClick()
        }
        binding.stopCard.setOnClickListener {
            data.onStopClick()
        }
    }

    private fun applyStandaloneMargins() {
        val spacing = (10f * itemView.resources.displayMetrics.density).toInt()
        val params = itemView.layoutParams as? RecyclerView.LayoutParams ?: return
        if (params.topMargin == spacing && params.bottomMargin == spacing) return
        params.topMargin = spacing
        params.bottomMargin = spacing
        itemView.layoutParams = params
    }
}
