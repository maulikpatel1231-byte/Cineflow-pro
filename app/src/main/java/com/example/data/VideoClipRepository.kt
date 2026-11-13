package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.model.VideoClip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class VideoClipRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("cineflow_clips_pref", Context.MODE_PRIVATE)

    private val _clips = MutableStateFlow<List<VideoClip>>(emptyList())
    val clips: StateFlow<List<VideoClip>> = _clips.asStateFlow()

    init {
        loadClips()
    }

    private fun loadClips() {
        val jsonStr = prefs.getString("saved_clips", null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<VideoClip>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val path = obj.getString("filePath")
                // Drop stale sample dummy entries or deleted files
                if (path.contains("SAMPLE_240FPS_WaterDrop") || path.contains("SAMPLE_120FPS_SkatePark")) {
                    continue
                }
                if (isClipAccessible(path)) {
                    list.add(
                        VideoClip(
                            id = obj.getString("id"),
                            filePath = path,
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
            }
            _clips.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isClipAccessible(pathOrUri: String): Boolean {
        return try {
            if (pathOrUri.startsWith("content://")) {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(pathOrUri), "r")?.use {
                    true
                } ?: false
            } else {
                val f = File(pathOrUri)
                f.exists() && f.length() > 0L
            }
        } catch (_: Exception) {
            false
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
                if (it.filePath.startsWith("content://")) {
                    context.contentResolver.delete(Uri.parse(it.filePath), null, null)
                } else {
                    val file = File(it.filePath)
                    if (file.exists()) file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val updated = _clips.value.filter { it.id != id }
        saveClips(updated)
    }
}
