package com.mp3player.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 接收外部广播的播放控制指令（通知栏按钮已通过 PendingIntent.getService 直接调用服务）
 */
class MusicBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val action = intent?.action ?: return
        if (action !in setOf(
                MusicPlayerService.ACTION_PLAY_PAUSE,
                MusicPlayerService.ACTION_NEXT,
                MusicPlayerService.ACTION_PREVIOUS,
                MusicPlayerService.ACTION_STOP
            )
        ) return

        val serviceIntent = Intent(ctx, MusicPlayerService::class.java).apply {
            this.action = action
        }
        try {
            ctx.startForegroundService(serviceIntent)
        } catch (_: Exception) {
            // Android 12+ 后台启动前台服务受限时忽略
        }
    }
}
