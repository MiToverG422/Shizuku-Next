package moe.shizuku.manager.home

import android.os.Bundle
import android.util.TypedValue
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import rikka.lifecycle.viewModels
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.addItemSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.widget.borderview.BorderRecyclerView

class ActivationMethodsActivity : AppBarActivity() {

    private val homeModel by viewModels { HomeViewModel() }
    private val adapter = ActivationMethodsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.apps_activity)
        supportActionBar?.title = getString(R.string.activation_methods_title)

        val recyclerView = findViewById<BorderRecyclerView>(android.R.id.list)
        recyclerView.fixEdgeEffect()
        recyclerView.adapter = adapter
        recyclerView.addItemSpacing(top = 1f, bottom = 1f, unit = TypedValue.COMPLEX_UNIT_DIP)
        recyclerView.addEdgeSpacing(
            top = 1f,
            bottom = 8f,
            left = 16f,
            right = 16f,
            unit = TypedValue.COMPLEX_UNIT_DIP
        )

        homeModel.serviceStatus.observe(this) {
            val status = it.data ?: return@observe
            adapter.updateData(status)
        }
    }

    override fun onResume() {
        super.onResume()
        homeModel.reload()
    }
}
