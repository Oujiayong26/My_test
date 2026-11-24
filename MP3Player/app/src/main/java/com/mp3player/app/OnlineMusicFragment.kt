package com.mp3player.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mp3player.app.adapter.MusicAdapter
import com.mp3player.app.adapter.OnlineMusicAdapter
import com.mp3player.app.data.Music
import com.mp3player.app.data.MusicRepository
import com.mp3player.app.databinding.DialogApiKeyBinding
import com.mp3player.app.databinding.FragmentOnlineBinding
import kotlinx.coroutines.launch

/** 在线发现页面 - 搜索 Jamendo 免费音乐 */
class OnlineMusicFragment : Fragment() {

    private var _binding: FragmentOnlineBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: OnlineMusicAdapter
    private var musicList: List<Music> = emptyList()
    private var lastQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnlineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = OnlineMusicAdapter(
            onItemClick = { index -> playAt(index) },
            onDownloadClick = { music -> download(music) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnSearch.setOnClickListener { doSearch() }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else false
        }

        binding.tvHint.setOnClickListener { showApiKeyDialog() }

        binding.swipeRefresh.setOnRefreshListener {
            if (lastQuery.isNotBlank()) doSearch()
            else binding.swipeRefresh.isRefreshing = false
        }

        updateHint()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(MusicPlayerService.ACTION_TRACK_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(trackReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(trackReceiver, filter)
        }
        updateHint()
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

    private fun updateHint() {
        val apiKey = MusicRepository.getApiKey(requireContext())
        binding.tvHint.visibility = if (apiKey.isBlank()) View.VISIBLE else View.GONE
    }

    private fun doSearch() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isBlank()) {
            Toast.makeText(requireContext(), "请输入搜索关键词", Toast.LENGTH_SHORT).show()
            return
        }
        val apiKey = MusicRepository.getApiKey(requireContext())
        if (apiKey.isBlank()) {
            showApiKeyDialog()
            return
        }

        lastQuery = query
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.btnSearch.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val list = MusicRepository.searchOnlineMusic(query, apiKey)
            val currentBinding = _binding ?: return@launch
            currentBinding.progressBar.visibility = View.GONE
            currentBinding.swipeRefresh.isRefreshing = false
            currentBinding.btnSearch.isEnabled = true
            currentBinding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            musicList = list
            adapter.submitList(list)
            if (list.isEmpty()) {
                Toast.makeText(requireContext(), R.string.search_error, Toast.LENGTH_SHORT).show()
            }
            updatePlayingIndex()
        }
    }

    private fun playAt(index: Int) {
        val service = (activity as? MainActivity)?.getMusicService()
        if (service == null) {
            Toast.makeText(requireContext(), "播放器正在初始化，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        if (musicList.isEmpty()) return
        service.setPlaylist(musicList, index)
        adapter.setPlayingIndex(index)
    }

    private fun download(music: Music) {
        Toast.makeText(requireContext(), R.string.downloading, Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val file = MusicRepository.downloadMusic(requireContext(), music)
            Toast.makeText(
                requireContext(),
                if (file != null) R.string.download_complete else R.string.download_failed,
                Toast.LENGTH_SHORT
            ).show()
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

    private fun showApiKeyDialog() {
        val dialogBinding = DialogApiKeyBinding.inflate(layoutInflater)
        dialogBinding.etApiKey.setText(MusicRepository.getApiKey(requireContext()))

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val key = dialogBinding.etApiKey.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(requireContext(), "请输入 API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MusicRepository.saveApiKey(requireContext(), key)
            dialog.dismiss()
            updateHint()
            Toast.makeText(requireContext(), "API Key 已保存，请重新搜索", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    companion object {
        fun formatDuration(ms: Long): String = MusicAdapter.formatDuration(ms)
    }
}
