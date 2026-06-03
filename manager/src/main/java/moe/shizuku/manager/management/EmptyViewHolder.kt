package moe.shizuku.manager.management

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.AppListEmptyBinding
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

data class EmptyState(@StringRes val messageRes: Int = R.string.home_app_management_empty)

class EmptyViewHolder(private val binding: AppListEmptyBinding) : BaseViewHolder<EmptyState>(binding.root) {

    override fun onBind() {
        binding.message.setText(data.messageRes)
    }

    companion object {
        @JvmField
        val CREATOR = Creator<EmptyState> { inflater: LayoutInflater, parent: ViewGroup? -> EmptyViewHolder(AppListEmptyBinding.inflate(inflater, parent, false)) }
    }

}
