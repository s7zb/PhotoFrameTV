package com.s7zb.photoframetv

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

class LibraryActivity : Activity() {
    private var files: List<File> = emptyList()
    private val selected = HashSet<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val loader = Executors.newFixedThreadPool(2)
    private val thumbs = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(48L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private lateinit var grid: GridView
    private lateinit var title: TextView
    private lateinit var empty: TextView
    private lateinit var adapter: Adapter
    private lateinit var btnAll: android.widget.Button
    private lateinit var btnDelete: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PhotoStore.init(applicationContext)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        loader.shutdownNow()
        thumbs.evictAll()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(COLOR_BG)
        root.setPadding(dp(48), dp(32), dp(48), dp(24))

        title = TextView(this)
        title.setTextColor(Color.WHITE)
        title.textSize = 28f
        title.typeface = Typeface.DEFAULT_BOLD
        root.addView(title)

        val tip = TextView(this)
        tip.text = "Press OK on a photo to select or unselect it"
        tip.setTextColor(COLOR_TEXT_DIM)
        tip.textSize = 18f
        tip.setPadding(0, dp(4), 0, dp(16))
        root.addView(tip)

        val holder = FrameLayout(this)
        root.addView(holder, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        grid = GridView(this)
        grid.numColumns = 5
        grid.horizontalSpacing = dp(12)
        grid.verticalSpacing = dp(12)
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.isFocusable = true
        val sel = GradientDrawable().apply {
            setColor(Color.TRANSPARENT); setStroke(dp(5), Color.WHITE); cornerRadius = dp(6).toFloat()
        }
        grid.selector = sel
        adapter = Adapter()
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ ->
            val p = files[pos].path
            if (!selected.remove(p)) selected.add(p)
            adapter.notifyDataSetChanged()
            updateTitle()
        }
        holder.addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        empty = TextView(this)
        empty.text = "No photos yet.\nGo back and scan the QR code with your phone to add some."
        empty.setTextColor(COLOR_TEXT_DIM)
        empty.textSize = 24f
        empty.gravity = Gravity.CENTER
        holder.addView(empty, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(0, dp(16), 0, 0)
        btnAll = tvButton("Select all")
        btnDelete = tvButton("Delete selected")
        val btnBack = tvButton("Back")
        for (b in listOf(btnAll, btnDelete, btnBack)) {
            val lp = LinearLayout.LayoutParams(dp(300), dp(64))
            lp.rightMargin = dp(16)
            bar.addView(b, lp)
        }
        root.addView(bar)

        btnAll.setOnClickListener {
            if (selected.size == files.size) selected.clear() else files.forEach { selected.add(it.path) }
            adapter.notifyDataSetChanged()
            updateTitle()
        }
        btnDelete.setOnClickListener { confirmDelete() }
        btnBack.setOnClickListener { finish() }

        setContentView(root)
    }

    private fun reload() {
        files = PhotoStore.list()
        selected.retainAll(files.map { it.path }.toSet())
        adapter.notifyDataSetChanged()
        grid.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
        empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        updateTitle()
        if (files.isEmpty()) btnAll.requestFocus() else grid.requestFocus()
    }

    private fun updateTitle() {
        val n = files.size
        title.text = "Photo library - $n ${if (n == 1) "photo" else "photos"}" +
            if (selected.isNotEmpty()) ", ${selected.size} selected" else ""
        btnAll.text = if (n > 0 && selected.size == n) "Unselect all" else "Select all"
    }

    private fun confirmDelete() {
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select photos first (press OK on a photo).", Toast.LENGTH_LONG).show()
            return
        }
        val count = selected.size
        AlertDialog.Builder(this)
            .setTitle("Delete $count ${if (count == 1) "photo" else "photos"}?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                var failed = 0
                for (p in selected.toList()) if (!PhotoStore.delete(File(p))) failed++
                selected.clear()
                thumbs.evictAll()
                reload()
                if (failed > 0) Toast.makeText(this, "$failed could not be deleted", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = files.size
        override fun getItem(position: Int): Any = files[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val cell: FrameLayout
            if (convertView == null) {
                cell = FrameLayout(this@LibraryActivity)
                cell.setBackgroundColor(COLOR_PANEL)
                cell.layoutParams = AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, dp(150))
                val img = ImageView(this@LibraryActivity)
                img.scaleType = ImageView.ScaleType.FIT_CENTER
                cell.addView(img, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                val name = TextView(this@LibraryActivity)
                name.setTextColor(Color.WHITE)
                name.textSize = 13f
                name.maxLines = 1
                name.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                name.setBackgroundColor(0xAA000000.toInt())
                name.setPadding(dp(6), dp(2), dp(6), dp(2))
                cell.addView(name, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
                val check = TextView(this@LibraryActivity)
                check.text = "\u2713"
                check.setTextColor(Color.WHITE)
                check.textSize = 22f
                check.gravity = Gravity.CENTER
                check.setBackgroundColor(COLOR_ACCENT)
                cell.addView(check, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.END))
                cell.tag = arrayOf<Any>(img, name, check)
            } else {
                cell = convertView as FrameLayout
            }
            @Suppress("UNCHECKED_CAST")
            val parts = cell.tag as Array<Any>
            val iv = parts[0] as ImageView
            val nm = parts[1] as TextView
            val ck = parts[2] as TextView

            val f = files[position]
            nm.text = PhotoStore.displayName(f)
            ck.visibility = if (selected.contains(f.path)) View.VISIBLE else View.GONE
            iv.alpha = if (selected.contains(f.path)) 0.55f else 1f
            iv.tag = f.path
            val cached = thumbs.get(f.path)
            if (cached != null) {
                iv.setImageBitmap(cached)
            } else {
                iv.setImageDrawable(null)
                try {
                    loader.execute {
                        val b = ImageLoader.decode(f, 320, 240)
                        if (b != null) {
                            thumbs.put(f.path, b)
                            handler.post {
                                if (iv.tag == f.path) iv.setImageBitmap(b)
                            }
                        }
                    }
                } catch (e: Exception) { }
            }
            return cell
        }
    }
}
