package com.mp3player.app.data

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 数据仓库：负责本地音乐扫描、在线音乐搜索与下载
 */
object MusicRepository {

    private const val PREFS_NAME = "mp3player_prefs"
    private const val KEY_API_ID = "jamendo_client_id"
    private const val JAMENDO_API = "https://api.jamendo.com/v3.0/tracks/"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getApiKey(context: Context): String =
        prefs(context).getString(KEY_API_ID, "") ?: ""

    fun saveApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API_ID, key.trim()).apply()
    }

    // ---------------------------------------------------------------------
    // 本地音乐
    // ---------------------------------------------------------------------

    /** 扫描设备上的本地 MP3 文件 */
    suspend fun scanLocalMusic(context: Context): List<Music> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Music>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val duration = cursor.getLong(durationCol)
                    if (duration < 1000) continue // 过滤过短文件

                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val title = cursor.getString(titleCol) ?: "未知歌曲"
                    val artist = cursor.getString(artistCol).takeUnless { it.isNullOrBlank() || it == "<unknown>" }
                        ?: "未知艺术家"
                    val album = cursor.getString(albumCol).takeUnless { it.isNullOrBlank() || it == "<unknown>" }
                        ?: "未知专辑"

                    result.add(
                        Music(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            path = contentUri.toString(),
                            duration = duration
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        result
    }

    // ---------------------------------------------------------------------
    // 在线音乐（Jamendo -  Creative Commons 免费音乐）
    // ---------------------------------------------------------------------

    /** 搜索 Jamendo 免费音乐 */
    suspend fun searchOnlineMusic(query: String, clientId: String): List<Music> =
        withContext(Dispatchers.IO) {
            if (clientId.isBlank()) return@withContext emptyList()

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "$JAMENDO_API?client_id=$clientId&format=json" +
                    "&search=$encodedQuery&limit=40&audioformat=mp32&include=musicinfo&order=popularity_total"

            val json = httpGet(urlString) ?: return@withContext emptyList()
            val result = mutableListOf<Music>()

            try {
                val root = JSONObject(json)
                val status = root.optJSONObject("head")?.optString("status")
                if (status == "success") {
                    val results = root.optJSONArray("results") ?: return@withContext emptyList()
                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        val audioUrl = item.optString("audio")
                        if (audioUrl.isNullOrBlank()) continue

                        result.add(
                            Music(
                                id = item.optString("id"),
                                title = item.optString("name", "未知歌曲"),
                                artist = item.optString("artist_name", "未知艺术家"),
                                album = item.optString("album_name", "未知专辑"),
                                path = audioUrl,
                                duration = (item.optDouble("duration", 0.0) * 1000).toLong(),
                                isOnline = true,
                                imageUrl = item.optString("image").ifBlank { null },
                                audioUrl = audioUrl
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
            result
        }

    /** 下载在线音乐到本地音乐目录 */
    suspend fun downloadMusic(context: Context, music: Music): File? = withContext(Dispatchers.IO) {
        val url = music.audioUrl ?: return@withContext null
        val baseDir = context.getExternalFilesDir("Music") ?: context.filesDir
        val musicDir = File(baseDir, "Downloads").apply { mkdirs() }
        val safeName = "${music.artist} - ${music.title}"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(100)
        val file = File(musicDir, "$safeName.mp3")

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 20000
                readTimeout = 30000
            }
            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (input.read(buffer).also { len = it } != -1) {
                        output.write(buffer, 0, len)
                    }
                }
            }
            connection.disconnect()
            file
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    private fun httpGet(urlString: String): String? {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        return try {
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "MP3Player/1.0")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { stream ->
                    BufferedReader(stream.reader()).use { it.readText() }
                }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
