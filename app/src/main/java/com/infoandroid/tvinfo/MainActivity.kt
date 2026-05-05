package com.infoandroid.tvinfo

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private var lastModeRequestStatus: String? = null
    private var lastFrameRateRequestStatus: String? = null
    private val probeLog = mutableListOf<String>()
    private lateinit var frameRateSurface: SurfaceView
    private var frameRateSurfaceReady: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val subtitle = findViewById<TextView>(R.id.headerSubtitle)
        subtitle.text = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}"
        frameRateSurface = findViewById(R.id.frameRateSurface)
        frameRateSurface.holder.addCallback(this)

        refreshUi()
    }

    private fun refreshUi() {
        val recycler = findViewById<RecyclerView>(R.id.recyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = DisplayReportAdapter(
            rows = buildRows(),
            onModeRequested = { modeId -> requestDisplayMode(modeId) },
            onFrameRateRequested = { fps -> requestFrameRate(fps) },
            onActionRequested = { action ->
                when (action) {
                    ActionType.RUN_PROBE -> runProbeMatrix()
                    ActionType.SHARE_REPORT -> shareProbeLog()
                }
            }
        )
    }

    private fun requestDisplayMode(modeId: Int) {
        val beforeModeId = windowManager.defaultDisplay.mode.modeId
        val params = window.attributes
        params.preferredDisplayModeId = modeId
        window.attributes = params
        Toast.makeText(this, "Requested modeId=$modeId", Toast.LENGTH_SHORT).show()

        window.decorView.postDelayed({
            val afterModeId = windowManager.defaultDisplay.mode.modeId
            lastModeRequestStatus = if (afterModeId == modeId) {
                "Applied requested modeId=$modeId"
            } else {
                "Not applied. Before=$beforeModeId, now=$afterModeId, requested=$modeId"
            }
            refreshUi()
        }, 900)
    }

    private fun requestFrameRate(targetFps: Float) {
        requestFrameRateInternal(targetFps) { _, status ->
            lastFrameRateRequestStatus = status
            Toast.makeText(this, "Requested ${roundHz(targetFps)} Hz", Toast.LENGTH_SHORT).show()
            refreshUi()
        }
    }

    private fun requestFrameRateInternal(targetFps: Float, onDone: (Boolean, String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onDone(false, "Not supported on this Android version (requires API 30+)")
            return
        }

        val beforeMode = windowManager.defaultDisplay.mode
        if (!frameRateSurfaceReady || !frameRateSurface.holder.surface.isValid) {
            onDone(false, "Surface not ready for frame-rate request (wait 1-2s after launch)")
            return
        }

        // Ensure the app has produced at least one frame on this surface
        frameRateSurface.holder.lockCanvas()?.let { canvas ->
            canvas.drawColor(android.graphics.Color.BLACK)
            frameRateSurface.holder.unlockCanvasAndPost(canvas)
        } ?: run {
            onDone(false, "Surface lockCanvas failed; surface may not be initialized yet")
            return
        }

        val apiUsed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            frameRateSurface.holder.surface.setFrameRate(
                targetFps,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS
            )
            "Surface.setFrameRate(fps, FIXED_SOURCE, ALWAYS)"
        } else {
            frameRateSurface.holder.surface.setFrameRate(
                targetFps,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
            )
            "Surface.setFrameRate(fps, FIXED_SOURCE)"
        }

        window.decorView.postDelayed({
            val afterMode = windowManager.defaultDisplay.mode
            val applied = Math.abs(afterMode.refreshRate - targetFps) < 1.5f
            val status = if (applied) {
                "Applied ${roundHz(targetFps)} Hz (${formatMode(afterMode)}) via $apiUsed"
            } else {
                "Not exact. requested=${roundHz(targetFps)} Hz, before=${formatMode(beforeMode)}, now=${formatMode(afterMode)} via $apiUsed"
            }
            onDone(applied, status)
        }, 1200)
    }

    private fun runProbeMatrix() {
        val targets = listOf(
            Triple(1920, 1080, 60f),
            Triple(1920, 1080, 120f),
            Triple(2560, 1440, 60f),
            Triple(2560, 1440, 120f),
            Triple(3840, 2160, 60f),
            Triple(3840, 2160, 120f)
        )

        probeLog.clear()
        probeLog += "Probe started on ${Build.MANUFACTURER} ${Build.MODEL}"

        fun step(index: Int) {
            if (index >= targets.size) {
                lastFrameRateRequestStatus = "Probe complete (${targets.size} tests)"
                refreshUi()
                return
            }

            val (w, h, fps) = targets[index]
            val display = windowManager.defaultDisplay
            val modes = display.supportedModes
            val before = display.mode

            val normalizedW = maxOf(w, h)
            val normalizedH = minOf(w, h)
            val exactMode = modes.firstOrNull {
                maxOf(it.physicalWidth, it.physicalHeight) == normalizedW &&
                        minOf(it.physicalWidth, it.physicalHeight) == normalizedH &&
                        Math.abs(it.refreshRate - fps) < 1.5f
            }
            val resMode = modes.firstOrNull {
                maxOf(it.physicalWidth, it.physicalHeight) == normalizedW &&
                        minOf(it.physicalWidth, it.physicalHeight) == normalizedH
            }

            val chosenMode = exactMode ?: resMode
            if (chosenMode != null) {
                val params = window.attributes
                params.preferredDisplayModeId = chosenMode.modeId
                window.attributes = params
            }

            requestFrameRateInternal(fps) { _, _ ->
                val after = windowManager.defaultDisplay.mode
                val modeApplied = chosenMode != null && after.modeId == chosenMode.modeId
                val fpsApplied = Math.abs(after.refreshRate - fps) < 1.5f
                val line = "${normalizedW}x${normalizedH}@${roundHz(fps)} -> " +
                        "modeRequest=" + (chosenMode?.modeId?.toString() ?: "none") +
                        ", before=${formatMode(before)}, after=${formatMode(after)}, " +
                        "modeApplied=$modeApplied, fpsApplied=$fpsApplied"
                probeLog += line
                lastFrameRateRequestStatus = line
                refreshUi()
                window.decorView.postDelayed({ step(index + 1) }, 500)
            }
        }

        step(0)
    }

    private fun shareProbeLog() {
        val text = if (probeLog.isEmpty()) "No probe results yet" else probeLog.joinToString("\n")
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "info-android probe report")
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share probe report"))
    }

    private fun buildRows(): List<Row> {
        val d = windowManager.defaultDisplay
        val modes = d.supportedModes.sortedWith(compareBy<Display.Mode>(
            { maxOf(it.physicalWidth, it.physicalHeight) },
            { minOf(it.physicalWidth, it.physicalHeight) },
            { it.refreshRate }
        ))

        val rows = mutableListOf<Row>()

        rows += Row.Section("Current mode")
        rows += Row.Item(
            title = formatMode(d.mode),
            subtitle = "Active display mode"
        )
        rows += Row.Item(
            title = "Frame-rate surface",
            subtitle = if (frameRateSurfaceReady) "Ready" else "Not ready yet"
        )
        lastModeRequestStatus?.let {
            rows += Row.Item("Mode switch result", it)
        }
        lastFrameRateRequestStatus?.let {
            rows += Row.Item("Frame-rate request result", it)
        }

        rows += Row.Section("Frame-rate API tests")
        val fpsTargets = listOf(24f, 30f, 50f, 60f, 90f, 120f)
        fpsTargets.forEach { fps ->
            rows += Row.FrameRateItem(
                title = "Request ${roundHz(fps)} Hz",
                subtitle = "Use modern frame-rate API and verify actual mode",
                fps = fps
            )
        }

        rows += Row.Section("Phase B probes")
        rows += Row.ActionItem("Run probe matrix", "Test 1080p/1440p/4K at 60/120 and log results", ActionType.RUN_PROBE)
        rows += Row.ActionItem("Share probe report", "Share collected probe diagnostics", ActionType.SHARE_REPORT)
        if (probeLog.isNotEmpty()) {
            rows += Row.Item("Latest probe", probeLog.last())
        }

        rows += Row.Section("Resolution → refresh rates")
        modes.groupBy {
            val w = maxOf(it.physicalWidth, it.physicalHeight)
            val h = minOf(it.physicalWidth, it.physicalHeight)
            "${w}x${h}"
        }.forEach { (res, list) ->
            val rates = list.map { roundHz(it.refreshRate) }.distinct().sorted()
            rows += Row.Item(
                title = res,
                subtitle = rates.joinToString("  •  ") { "${it} Hz" }
            )
        }

        rows += Row.Section("All supported modes (${modes.size})")
        modes.forEach { mode ->
            rows += Row.ModeItem(
                title = formatMode(mode),
                subtitle = "modeId=${mode.modeId}  •  Click to request this mode",
                modeId = mode.modeId
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            rows += Row.Section("HDR metadata")
            val hdr = d.hdrCapabilities
            if (hdr == null) {
                rows += Row.Item("Unavailable", "Device did not return HDR capabilities")
            } else {
                rows += Row.Item("Max luminance", hdr.desiredMaxLuminance.toString())
                rows += Row.Item("Max average luminance", hdr.desiredMaxAverageLuminance.toString())
                rows += Row.Item("Min luminance", hdr.desiredMinLuminance.toString())
                rows += Row.Item("Supported HDR types", hdr.supportedHdrTypes.joinToString())
            }
        }

        return rows
    }

    private fun formatMode(mode: Display.Mode): String {
        val w = maxOf(mode.physicalWidth, mode.physicalHeight)
        val h = minOf(mode.physicalWidth, mode.physicalHeight)
        return "${w}x${h} @ ${roundHz(mode.refreshRate)} Hz"
    }

    private fun roundHz(rate: Float): String = String.format(Locale.US, "%.2f", rate)

    override fun surfaceCreated(holder: SurfaceHolder) {
        frameRateSurfaceReady = true
        refreshUi()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        frameRateSurfaceReady = true
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        frameRateSurfaceReady = false
    }
}

private enum class ActionType { RUN_PROBE, SHARE_REPORT }

private sealed class Row {
    data class Section(val text: String) : Row()
    data class Item(val title: String, val subtitle: String) : Row()
    data class ModeItem(val title: String, val subtitle: String, val modeId: Int) : Row()
    data class FrameRateItem(val title: String, val subtitle: String, val fps: Float) : Row()
    data class ActionItem(val title: String, val subtitle: String, val action: ActionType) : Row()
}

private class DisplayReportAdapter(
    private val rows: List<Row>,
    private val onModeRequested: (Int) -> Unit,
    private val onFrameRateRequested: (Float) -> Unit,
    private val onActionRequested: (ActionType) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SECTION = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is Row.Section -> TYPE_SECTION
            is Row.Item, is Row.ModeItem, is Row.FrameRateItem, is Row.ActionItem -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SECTION) {
            SectionVH(inflater.inflate(R.layout.item_section, parent, false))
        } else {
            ItemVH(inflater.inflate(R.layout.item_mode, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Section -> (holder as SectionVH).bind(row)
            is Row.Item -> (holder as ItemVH).bind(row.title, row.subtitle, null)
            is Row.ModeItem -> (holder as ItemVH).bind(row.title, row.subtitle) {
                onModeRequested(row.modeId)
            }
            is Row.FrameRateItem -> (holder as ItemVH).bind(row.title, row.subtitle) {
                onFrameRateRequested(row.fps)
            }
            is Row.ActionItem -> (holder as ItemVH).bind(row.title, row.subtitle) {
                onActionRequested(row.action)
            }
        }
    }

    override fun getItemCount(): Int = rows.size

    private class SectionVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.sectionTitle)
        fun bind(row: Row.Section) {
            title.text = row.text
        }
    }

    private class ItemVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.title)
        private val subtitle = itemView.findViewById<TextView>(R.id.subtitle)

        fun bind(titleText: String, subtitleText: String, onClick: (() -> Unit)?) {
            title.text = titleText
            subtitle.text = subtitleText
            itemView.setOnClickListener {
                onClick?.invoke()
            }
        }
    }
}
