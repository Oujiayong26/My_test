package com.mp3player.app.data

/**
 * 音乐数据模型，本地和在线音乐统一用此类表示
 */
data class Music(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val duration: Long,
    val isOnline: Boolean = false,
    val imageUrl: String? = null,
    val audioUrl: String? = null
) {
    /** 播放器实际使用的音频地址 */
    val playableUrl: String get() = if (isOnline) (audioUrl ?: path) else path
}
