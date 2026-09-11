package moe.shizuku.manager.shell

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ui.component.*
import moe.shizuku.manager.ui.theme.ShizukuTheme
import moe.shizuku.manager.utils.CustomTabsHelper

class ShellTutorialActivity : AppActivity() {

    companion object {

        private val SH_NAME = "rish"
        private val DEX_NAME = "rish_shizuku.dex"
    }

    private val openDocumentsTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
            if (tree == null) return@registerForActivityResult

            val cr = contentResolver
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val child =
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

            cr.query(
                child,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    val name = it.getString(1)
                    if (name == SH_NAME || name == DEX_NAME) {
                        DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id))
                    }
                }
            }

            fun writeToDocument(name: String) {
                DocumentsContract.createDocument(contentResolver, doc, "application/octet-stream", name)?.runCatching {
                    cr.openOutputStream(this)?.let { assets.open(name).copyTo(it) }
                }
            }

            writeToDocument(SH_NAME)
            writeToDocument(DEX_NAME)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShizukuTheme {
                MaterialPage(stringResource(R.string.home_terminal_title), { finish() }) {
                    LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        item {
                            TonalCard { Column(Modifier.padding(16.dp)) {
                                HtmlText(getString(R.string.rish_description, SH_NAME))
                            } }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(UiMetrics.SegmentGap)) {
                                SegmentedCard(0, 3) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        HtmlText(getString(R.string.terminal_tutorial_1, SH_NAME, DEX_NAME))
                                        HtmlText(getString(R.string.terminal_tutorial_1_description))
                                        Button(onClick = { openDocumentsTree.launch(null) }) {
                                            Text(stringResource(R.string.terminal_export_files))
                                        }
                                    }
                                }
                                SegmentedCard(1, 3) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        HtmlText(getString(R.string.terminal_tutorial_2, SH_NAME))
                                        HtmlText(getString(R.string.terminal_tutorial_2_description,
                                            "Termux", "PKG", "com.termux", "com.termux"))
                                    }
                                }
                                SegmentedCard(2, 3) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        HtmlText(getString(R.string.terminal_tutorial_3, "sh $SH_NAME"))
                                        HtmlText(getString(R.string.terminal_tutorial_3_description, SH_NAME, "PATH"))
                                    }
                                }
                            }
                        }
                        item {
                            MaterialRow(stringResource(R.string.home_learn_more_title),
                                icon = R.drawable.ic_outline_open_in_new_24,
                                onClick = { CustomTabsHelper.launchUrlOrCopy(this@ShellTutorialActivity, Helps.RISH.get()) })
                        }
                    }
                }
            }
        }
    }
}
