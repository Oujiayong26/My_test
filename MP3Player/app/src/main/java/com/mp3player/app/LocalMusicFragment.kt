package com.mp3player.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mp3player.app.adapter.MusicAdapter
import com.mp3player.app.data.Music
import com.mp3player.app.data.MusicRepository
import com.mp3player.app.databinding.FragmentLocalBinding
import kotlinx.coroutines.launch

/** 本地音乐列表页面 */
class LocalMusicFragment : Fragment() {

    private var _binding: FragmentLocalBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MusicAdapter
    private var musicList: List<Music> = emptyList()

    private val permissionRequestCode = 100

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MusicAdapter { index -> playAt(index) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadMusic() }

        if (hasPermission()) {
            loadMusic()
        } else {
            requestPermissions(
                arrayOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        Manifest.permission.READ_MEDIA_AUDIO
                    else Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                permissionRequestCode
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(MusicPlayerService.ACTION_TRACK_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(trackReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(trackReceiver, filter)
        }
        updatePlayingIndex()
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(trackReceiver)
        } catch (_: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun playAt(index: Int) {
        val service = (activity as? MainActivity)?.getMusicService()
        if (service == null) {
            Toast.makeText(requireContext(), "播放器正在初始化，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        if (musicList.isEmpty()) {
            Toast.makeText(requireContext(), "没有可播放的音乐", Toast.LENGTH_SHORT).show()
            return
        }
        service.setPlaylist(musicList, index)
        adapter.setPlayingIndex(index)
    }

    private fun loadMusic() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            musicList = MusicRepository.scanLocalMusic(requireContext())
            val currentBinding = _binding ?: return@launch
            currentBinding.progressBar.visibility = View.GONE
            currentBinding.swipeRefresh.isRefreshing = false
            currentBinding.tvEmpty.visibility =
                if (musicList.isEmpty()) View.VISIBLE else View.GONE
            adapter.submitList(musicList)
            updatePlayingIndex()
        }
    }

    private fun updatePlayingIndex() {
        val current = (activity as? MainActivity)?.getMusicService()?.getCurrentMusic()
        val index = musicList.indexOfFirst { it.id == current?.id }
        adapter.setPlayingIndex(if (index >= 0) index else -1)
    }

    private val trackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updatePlayingIndex()
        }
    }

    private fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(requireContext(), permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMusic()
            } else {
                Toast.makeText(requireContext(), R.string.permission_required, Toast.LENGTH_LONG).show()
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }
    }
}
