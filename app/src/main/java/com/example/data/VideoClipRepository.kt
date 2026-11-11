package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.VideoClip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class VideoClipRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("cineflow_clips_pref", Context.MODE_PRIVATE)

    private val _clips = MutableStateFlow<List<VideoClip>>(emptyList())
    val clips: StateFlow<List<VideoClip>> = _clips.asStateFlow()

    init {
        loadClips()
        if (_clips.value.isEmpty()) {
            seedSampleClips()
        }
    }

    private fun loadClips() {
        val jsonStr = prefs.getString("saved_clips", null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<VideoClip>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    VideoClip(
                        id = obj.getString("id"),
                        filePath = obj.getString("filePath"),
                        title = obj.getString("title"),
                        timestamp = obj.getLong("timestamp"),
                        durationSeconds = obj.getLong("durationSeconds"),
                        fps = obj.getInt("fps"),
                        resolution = obj.getString("resolution"),
                        filterName = obj.getString("filterName"),
                        stabilization = obj.getString("stabilization"),
                        iso = obj.getInt("iso"),
                        shutterSpeedText = obj.getString("shutterSpeedText"),
                        isSlowMotion = obj.getBoolean("isSlowMotion"),
                        fileSizeFormatted = obj.getString("fileSizeFormatted")
                    )
                )
            }
            _clips.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveClips(list: List<VideoClip>) {
        val jsonArray = JSONArray()
        for (clip in list) {
            val obj = JSONObject().apply {
                put("id", clip.id)
                put("filePath", clip.filePath)
                put("title", clip.title)
                put("timestamp", clip.timestamp)
                put("durationSeconds", clip.durationSeconds)
                put("fps", clip.fps)
                put("resolution", clip.resolution)
                put("filterName", clip.filterName)
                put("stabilization", clip.stabilization)
                put("iso", clip.iso)
                put("shutterSpeedText", clip.shutterSpeedText)
                put("isSlowMotion", clip.isSlowMotion)
                put("fileSizeFormatted", clip.fileSizeFormatted)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("saved_clips", jsonArray.toString()).apply()
        _clips.value = list
    }

    fun addClip(clip: VideoClip) {
        val updated = listOf(clip) + _clips.value
        saveClips(updated)
    }

    fun deleteClip(id: String) {
        val clip = _clips.value.find { it.id == id }
        clip?.let {
            try {
                val file = File(it.filePath)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val updated = _clips.value.filter { it.id != id }
        saveClips(updated)
    }

    private fun seedSampleClips() {
        // Create demo slow-mo files for preview in case camera is in emulator
        val dir = File(context.filesDir, "CineFlowVideos").apply { if (!exists()) mkdirs() }
        val sample1 = File(dir, "SAMPLE_240FPS_WaterDrop.mp4")
        val sample2 = File(dir, "SAMPLE_120FPS_SkatePark.mp4")
        if (!sample1.exists()) {
            sample1.writeBytes(ByteArray(1024 * 512))
        }
        if (!sample2.exists()) {
            sample2.writeBytes(ByteArray(1024 * 384))
        }

        val samples = listOf(
            VideoClip(
                id = UUID.randomUUID().toString(),
                filePath = sample1.absolutePath,
                title = "Water Drop Splash (240 FPS Ultra Slow-Mo)",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 35,
                durationSeconds = 12L,
                fps = 240,
                resolution = "1080p FHD",
                filterName = "Teal & Orange",
                stabilization = "Hybrid Pro",
                iso = 100,
                shutterSpeedText = "1/500s",
                isSlowMotion = true,
                fileSizeFormatted = "48.2 MB"
            ),
            VideoClip(
                id = UUID.randomUUID().toString(),
                filePath = sample2.absolutePath,
                title = "Skatepark Kickflip (120 FPS High Speed)",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 120,
                durationSeconds = 8L,
                fps = 120,
                resolution = "720p HD",
                filterName = "Cyberpunk",
                stabilization = "OIS (Optical)",
                iso = 400,
                shutterSpeedText = "1/240s",
                isSlowMotion = true,
                fileSizeFormatted = "28.6 MB"
            )
        )
        saveClips(samples)
    }
}
