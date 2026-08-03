package com.tasirin.httpdownloadmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tasirin.httpdownloadmanager.data.DownloadItem
import com.tasirin.httpdownloadmanager.data.DownloadState
import com.tasirin.httpdownloadmanager.databinding.ActivityMainBinding
import com.tasirin.httpdownloadmanager.ui.DownloadAdapter
import com.tasirin.httpdownloadmanager.util.MimeTypes
import com.tasirin.httpdownloadmanager.remote.HttpControlServer
import com.tasirin.httpdownloadmanager.util.StoragePrefs
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity(), DownloadAdapter.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DownloadAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* hasil izin tidak wajib untuk fungsi inti */ }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val name = DocumentFile.fromTreeUri(this, uri)?.name
            StoragePrefs.saveFolder(this, uri, name)
            val label = name ?: getString(R.string.storage_custom_folder)
            Snackbar.make(
                binding.root,
                getString(R.string.storage_folder_selected, label),
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        adapter = DownloadAdapter(this)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddDialog() }

        lifecycleScope.launch {
            App.engine.items.collect { items ->
                adapter.submitList(items)
                binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT in 23..28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_download, null)
        val urlInput = view.findViewById<EditText>(R.id.input_url)
        val nameInput = view.findViewById<EditText>(R.id.input_name)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_download)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.download) { _, _ ->
                val url = urlInput.text?.toString()?.trim().orEmpty()
                if (url.isEmpty()) {
                    Snackbar.make(binding.root, R.string.invalid_url, Snackbar.LENGTH_SHORT).show()
                } else {
                    App.engine.addDownload(url, nameInput.text?.toString())
                }
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_completed -> {
                App.engine.clearCompleted()
                true
            }
            R.id.action_storage -> {
                showStorageDialog()
                true
            }
            R.id.action_remote -> {
                showRemoteDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onAction(item: DownloadItem, action: DownloadAdapter.Action) {
        when (action) {
            DownloadAdapter.Action.PAUSE -> App.engine.pause(item.id)
            DownloadAdapter.Action.RESUME -> App.engine.resume(item.id)
            DownloadAdapter.Action.CANCEL -> App.engine.cancel(item.id)
            DownloadAdapter.Action.DELETE -> App.engine.remove(item.id)
            DownloadAdapter.Action.OPEN -> openDownload(item)
        }
    }

    private fun showStorageDialog() {
        val currentName = StoragePrefs.getFolderName(this)
            ?: getString(R.string.storage_default_folder)
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_storage)
            .setMessage(getString(R.string.storage_current, currentName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.storage_choose_folder) { _, _ ->
                folderPicker.launch(null)
            }
        if (StoragePrefs.getFolderUri(this) != null) {
            builder.setNeutralButton(R.string.storage_use_default) { _, _ ->
                StoragePrefs.saveFolder(this, null, null)
                Snackbar.make(
                    binding.root,
                    R.string.storage_default_folder,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
        builder.show()
    }

    private fun showRemoteDialog() {
        val server = App.httpServer
        val status = if (server.isAlive) {
            getString(R.string.remote_running)
        } else {
            getString(R.string.remote_stopped)
        }
        val urls = if (server.isAlive) {
            HttpControlServer.ipv4Addresses()
                .joinToString("\n") { "http://$it:${server.listeningPort}/" }
                .ifEmpty { getString(R.string.remote_no_url) }
        } else {
            getString(R.string.remote_no_url)
        }
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_remote)
            .setMessage(getString(R.string.remote_message, status, urls))
            .setNegativeButton(R.string.cancel, null)
        if (server.isAlive) {
            builder.setPositiveButton(R.string.remote_stop) { _, _ ->
                server.stopServer()
                Snackbar.make(binding.root, R.string.remote_stopped, Snackbar.LENGTH_SHORT).show()
            }
        } else {
            builder.setPositiveButton(R.string.remote_start) { _, _ ->
                runCatching { server.startServer() }
                    .onSuccess {
                        Snackbar.make(
                            binding.root,
                            getString(R.string.remote_started, server.listeningPort),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    .onFailure {
                        Snackbar.make(
                            binding.root,
                            getString(R.string.remote_start_failed, it.message ?: "?"),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
            }
        }
        builder.show()
    }

    private fun openDownload(item: DownloadItem) {
        if (item.state != DownloadState.COMPLETED) return
        val mime = MimeTypes.forFile(item.fileName)
        val intent = when {
            !item.contentUri.isNullOrEmpty() -> {
                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.contentUri), mime)
            }
            !item.filePath.isNullOrEmpty() -> {
                val uri = FileProvider.getUriForFile(
                    this, "$packageName.fileprovider", File(item.filePath)
                )
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            else -> null
        }
        if (intent != null) {
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Snackbar.make(binding.root, R.string.no_app_to_open, Snackbar.LENGTH_SHORT).show()
            }
        }
    }
}
