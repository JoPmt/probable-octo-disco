package com.quantum.agent

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MusicFileManager(private val context: Context) {
    companion object {
        private const val TAG = "MusicFileManager"
        private const val PREFS_NAME = "quantum_ace_step_music_prefs"
        private const val KEY_TRACKS_JSON = "saved_generated_tracks"
        private const val MUSIC_DIR_NAME = "music"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val musicDirectory: File by lazy {
        val dir = File(context.filesDir, MUSIC_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    /**
     * Loads all generated tracks from disk and metadata index.
     */
    fun loadAllTracks(): List<GeneratedTrackItem> {
        val jsonStr = prefs.getString(KEY_TRACKS_JSON, null) ?: return emptyList()
        val list = mutableListOf<GeneratedTrackItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val filePath = obj.optString("filePath")
                val file = File(filePath)
                
                // Keep tracks whose files exist
                if (file.exists() && file.length() > 0) {
                    val waveformArray = obj.optJSONArray("waveformPoints")
                    val waveformList = mutableListOf<Float>()
                    if (waveformArray != null) {
                        for (w in 0 until waveformArray.length()) {
                            waveformList.add(waveformArray.getDouble(w).toFloat())
                        }
                    }

                    list.add(
                        GeneratedTrackItem(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            filePath = filePath,
                            fileName = obj.optString("fileName", file.name),
                            fileSizeBytes = if (obj.has("fileSizeBytes")) obj.optLong("fileSizeBytes") else file.length(),
                            durationSeconds = obj.optInt("durationSeconds", 30),
                            sampleRateHz = obj.optInt("sampleRateHz", 44100),
                            bpm = obj.optInt("bpm", 128),
                            musicalKey = obj.optString("musicalKey", "F Minor"),
                            prompt = obj.optString("prompt", ""),
                            lyrics = obj.optString("lyrics", ""),
                            genre = obj.optString("genre", "Synthwave"),
                            modelVariant = obj.optString("modelVariant", "ACE-Step 1.5 Turbo"),
                            sampler = obj.optString("sampler", "Flow-Matching ODE"),
                            seed = obj.optLong("seed", 42L),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            isFavorite = obj.optBoolean("isFavorite", false),
                            waveformPoints = waveformList
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing track list: ${e.message}", e)
        }
        return list.sortedByDescending { it.timestamp }
    }

    /**
     * Saves or updates a track in persistent storage.
     */
    @Synchronized
    fun saveTrack(track: GeneratedTrackItem) {
        val existing = loadAllTracks().toMutableList()
        val idx = existing.indexOfFirst { it.id == track.id }
        if (idx >= 0) {
            existing[idx] = track
        } else {
            existing.add(0, track)
        }
        persistTrackList(existing)
    }

    /**
     * Deletes track file and metadata from storage.
     */
    @Synchronized
    fun deleteTrack(trackId: String): Boolean {
        val existing = loadAllTracks().toMutableList()
        val target = existing.find { it.id == trackId }
        if (target != null) {
            try {
                val file = File(target.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete file on disk: ${e.message}")
            }
            existing.removeAll { it.id == trackId }
            persistTrackList(existing)
            return true
        }
        return false
    }

    /**
     * Toggles favorite status for a track.
     */
    @Synchronized
    fun toggleFavorite(trackId: String): GeneratedTrackItem? {
        val existing = loadAllTracks().toMutableList()
        val idx = existing.indexOfFirst { it.id == trackId }
        if (idx >= 0) {
            val updated = existing[idx].copy(isFavorite = !existing[idx].isFavorite)
            existing[idx] = updated
            persistTrackList(existing)
            return updated
        }
        return null
    }

    /**
     * Renames a track title.
     */
    @Synchronized
    fun renameTrack(trackId: String, newTitle: String): GeneratedTrackItem? {
        val existing = loadAllTracks().toMutableList()
        val idx = existing.indexOfFirst { it.id == trackId }
        if (idx >= 0) {
            val updated = existing[idx].copy(title = newTitle.trim())
            existing[idx] = updated
            persistTrackList(existing)
            return updated
        }
        return null
    }

    /**
     * Shares the generated audio file to external apps (Telegram, WhatsApp, Drive, Bluetooth, etc.)
     * using Android FileProvider and Intent.ACTION_SEND.
     */
    fun shareTrack(track: GeneratedTrackItem, launcherContext: Context = context) {
        val file = File(track.filePath)
        if (!file.exists()) {
            Log.e(TAG, "Cannot share track: file does not exist at ${track.filePath}")
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "🎵 ${track.title} (ACE-Step 1.5 C++ Neural Generation)"
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    "🎵 Listen to \"${track.title}\" generated with ACE-Step 1.5 C++ Audio Diffusion!\n" +
                            "• Genre: ${track.genre}\n" +
                            "• BPM: ${track.bpm} | Key: ${track.musicalKey}\n" +
                            "• Model: ${track.modelVariant}\n" +
                            "• Prompt: \"${track.prompt}\""
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share \"${track.title}\" via...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launcherContext.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating share sheet: ${e.message}", e)
        }
    }

    /**
     * Exports the audio track to public Downloads or Music directory via MediaStore.
     */
    fun exportTrackToPublicStorage(track: GeneratedTrackItem): Pair<Boolean, String> {
        val sourceFile = File(track.filePath)
        if (!sourceFile.exists()) {
            return Pair(false, "Source file not found at ${track.filePath}")
        }

        try {
            val fileName = "ACE_Step_${System.currentTimeMillis()}_${track.title.replace("[^a-zA-Z0-9]".toRegex(), "_")}.wav"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Quantum_ACE_Step")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return Pair(false, "Could not create MediaStore entry")

                resolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(output)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                return Pair(true, "Exported successfully to Music/Quantum_ACE_Step/$fileName")
            } else {
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val targetDir = File(musicDir, "Quantum_ACE_Step")
                if (!targetDir.exists()) targetDir.mkdirs()
                val targetFile = File(targetDir, fileName)
                sourceFile.copyTo(targetFile, overwrite = true)
                return Pair(true, "Saved to ${targetFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export error: ${e.message}", e)
            return Pair(false, "Export failed: ${e.message}")
        }
    }

    private fun persistTrackList(tracks: List<GeneratedTrackItem>) {
        val jsonArray = JSONArray()
        for (t in tracks) {
            val obj = JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("filePath", t.filePath)
                put("fileName", t.fileName)
                put("fileSizeBytes", t.fileSizeBytes)
                put("durationSeconds", t.durationSeconds)
                put("sampleRateHz", t.sampleRateHz)
                put("bpm", t.bpm)
                put("musicalKey", t.musicalKey)
                put("prompt", t.prompt)
                put("lyrics", t.lyrics)
                put("genre", t.genre)
                put("modelVariant", t.modelVariant)
                put("sampler", t.sampler)
                put("seed", t.seed)
                put("timestamp", t.timestamp)
                put("isFavorite", t.isFavorite)

                val waveArray = JSONArray()
                for (w in t.waveformPoints) {
                    waveArray.put(w.toDouble())
                }
                put("waveformPoints", waveArray)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_TRACKS_JSON, jsonArray.toString()).apply()
    }
}
