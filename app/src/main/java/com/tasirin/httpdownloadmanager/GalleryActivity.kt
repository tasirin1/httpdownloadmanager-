package com.tasirin.httpdownloadmanager

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tasirin.httpdownloadmanager.databinding.ActivityGalleryBinding
import com.tasirin.httpdownloadmanager.databinding.ItemGalleryBinding
import com.tasirin.httpdownloadmanager.util.MediaLibrary
import com.tasirin.httpdownloadmanager.util.MimeTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private val adapter = GalleryAdapter(
        loader = { e -> loadThumb(this, e, THUMB_SIZE) },
        onClick = { e -> openEntry(e) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        runCatching { installSplashScreen() }
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recycler.layoutManager = GridLayoutManager(this, SPAN_COUNT)
        binding.recycler.adapter = adapter

        lifecycleScope.launch {
            binding.progress.visibility = View.VISIBLE
            val list = withContext(Dispatchers.IO) { MediaLibrary.scan(this@GalleryActivity) }
            binding.progress.visibility = View.GONE
            adapter.submit(list)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openEntry(e: MediaLibrary.MediaEntry) {
        val mime = MimeTypes.forFile(e.name)
        val intent = when {
            !e.contentUri.isNullOrEmpty() ->
                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(e.contentUri), mime)
            !e.filePath.isNullOrEmpty() -> {
                val uri = FileProvider.getUriForFile(
                    this, "$packageName.fileprovider", File(e.filePath)
                )
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            else -> null
        }
        if (intent != null) {
            runCatching { startActivity(intent) }.onFailure {
                Toast.makeText(this, R.string.gallery_open_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val SPAN_COUNT = 3
        private const val THUMB_SIZE = 256

        suspend fun loadThumb(context: Context, e: MediaLibrary.MediaEntry, req: Int): Bitmap? =
            withContext(Dispatchers.IO) {
                when {
                    e.isVideo && !e.filePath.isNullOrEmpty() -> runCatching {
                        ThumbnailUtils.createVideoThumbnail(
                            e.filePath, MediaStore.Images.Thumbnails.MINI_KIND
                        )
                    }.getOrNull()

                    e.isVideo && !e.contentUri.isNullOrEmpty() &&
                        Build.VERSION.SDK_INT >= 29 -> runCatching {
                        context.contentResolver.loadThumbnail(
                            Uri.parse(e.contentUri), Size(req, req), null
                        )
                    }.getOrNull()

                    !e.isVideo && !e.filePath.isNullOrEmpty() ->
                        decodeFile(context, e.filePath, req)

                    !e.isVideo && !e.contentUri.isNullOrEmpty() ->
                        decodeUri(context, e.contentUri, req)

                    else -> null
                }
            }

        private fun decodeFile(context: Context, path: String, req: Int): Bitmap? =
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeSample(bounds, req)
                }
                BitmapFactory.decodeFile(path, opts)
            }.getOrNull()

        private fun decodeUri(context: Context, uri: String, req: Int): Bitmap? =
            runCatching {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeSample(bounds, req)
                }
                resolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()

        private fun computeSample(bounds: BitmapFactory.Options, req: Int): Int {
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return 1
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= req &&
                bounds.outHeight / (sample * 2) >= req
            ) {
                sample *= 2
            }
            return sample
        }
    }
}

private class GalleryAdapter(
    private val loader: suspend (MediaLibrary.MediaEntry) -> Bitmap?,
    private val onClick: (MediaLibrary.MediaEntry) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.Holder>() {

    private val items = mutableListOf<MediaLibrary.MediaEntry>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun submit(list: List<MediaLibrary.MediaEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemGalleryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemGalleryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val e = items[position]
        val b = holder.binding
        b.textName.text = e.name
        b.textExt.text = e.name.substringAfterLast('.', "").uppercase()
        b.imageThumb.setImageDrawable(null)
        val pos = position
        holder.itemView.setOnClickListener { onClick(e) }
        scope.launch {
            val bmp = loader(e)
            if (bmp != null && holder.bindingAdapterPosition == pos) {
                b.imageThumb.setImageBitmap(bmp)
            }
        }
    }
}
