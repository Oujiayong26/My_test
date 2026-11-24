package com.mp3player.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.mp3player.app.data.MusicRepository
import com.mp3player.app.databinding.ActivityMainBinding
import com.mp3player.app.databinding.DialogApiKeyBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 主界面：管理标签页、迷你播放器和播放服务连接 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var musicService: MusicPlayerService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicPlayerService.MusicBinder
            musicService = binder.getService()
            isBound = true
            updatePlayerBar()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.viewPager.adapter = ViewPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_local)
                else -> getString(R.string.tab_online)
            }
        }.attach()

        binding.playerBarInclude.btnPlayPause.setOnClickListener {
            musicService?.playPause()
            updatePlayerBar()
        }
        binding.playerBarInclude.btnNext.setOnClickListener {
            musicService?.next()
        }
        binding.playerBarInclude.root.setOnClickListener {
            FullPlayerDialog().show(supportFragmentManager, "FullPlayerDialog")
        }

        val intent = Intent(this, MusicPlayerService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        requestNotificationPermission()

        lifecycleScope.launch {
            while (true) {
                updateProgress()
                delay(500)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(MusicPlayerService.ACTION_TRACK_CHANGED)
            addAction(MusicPlayerService.ACTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(playerReceiver, filter)
        }
        updatePlayerBar()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(playerReceiver)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(connection)
    }

    fun getMusicService(): MusicPlayerService? = musicService

    private fun updatePlayerBar() {
        val service = musicService
        val bar = binding.playerBarInclude
        if (service == null) return

        val music = service.getCurrentMusic()
        if (music != null) {
            bar.tvSongTitle.text = music.title
            bar.tvSongArtist.text = music.artist
            bar.btnPlayPause.setImageResource(
                if (service.isPlaying()) R.drawable.ic_pause else R.drawable.ic_play
            )
        } else {
            bar.tvSongTitle.text = getString(R.string.app_name)
            bar.tvSongArtist.text = "选择歌曲开始播放"
            bar.btnPlayPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun updateProgress() {
        val service = musicService ?: return
        val duration = service.getDuration()
        if (duration > 0) {
            binding.playerBarInclude.progressBar.progress =
                (service.getCurrentPosition().toFloat() / duration * 1000).toInt()
        } else {
            binding.playerBarInclude.progressBar.progress = 0
        }
    }

    private val playerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MusicPlayerService.ACTION_TRACK_CHANGED,
                MusicPlayerService.ACTION_STATE_CHANGED -> updatePlayerBar()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            showApiKeyDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showApiKeyDialog() {
        val dialogBinding = DialogApiKeyBinding.inflate(layoutInflater)
        dialogBinding.etApiKey.setText(MusicRepository.getApiKey(this))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val key = dialogBinding.etApiKey.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MusicRepository.saveApiKey(this, key)
            dialog.dismiss()
            Toast.makeText(this, "API Key 已保存", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    /** ViewPager 适配器 */
    private class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> LocalMusicFragment()
            else -> OnlineMusicFragment()
        }
    }
}
