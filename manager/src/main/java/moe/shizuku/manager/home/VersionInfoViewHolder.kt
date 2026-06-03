package moe.shizuku.manager.home

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeVersionInfoBinding
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

class VersionInfoViewHolder(private val binding: HomeVersionInfoBinding, root: View) :
    BaseViewHolder<Any?>(root) {

    companion object {
        val CREATOR = Creator<Any> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeVersionInfoBinding.inflate(inflater, outer.root, true)
            VersionInfoViewHolder(inner, outer.root)
        }
    }

    override fun onBind() {
        HomeCardShapeHelper.applyStandaloneShape(itemView)
        applyStandaloneMargins()

        val context = itemView.context
        val deviceModel = buildDeviceModel()

        binding.appVersion.text = context.getString(
            R.string.home_version_info_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
        binding.androidVersion.text = context.getString(
            R.string.home_version_info_android_format,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT
        )
        binding.deviceModel.text = context.getString(
            R.string.home_version_info_model_format,
            deviceModel
        )
    }

    private fun applyStandaloneMargins() {
        val spacing = (10f * itemView.resources.displayMetrics.density).toInt()
        val params = itemView.layoutParams as? RecyclerView.LayoutParams ?: return
        if (params.topMargin == spacing && params.bottomMargin == spacing) return
        params.topMargin = spacing
        params.bottomMargin = spacing
        itemView.layoutParams = params
    }

    private fun buildDeviceModel(): String {
        val brand = Build.BRAND.trim()
        val model = Build.MODEL.trim()
        if (brand.isBlank() && model.isBlank()) return "-"
        if (brand.isBlank()) return model
        if (model.isBlank()) return brand
        if (model.startsWith(brand, ignoreCase = true)) return model
        return "$brand $model"
    }
}
