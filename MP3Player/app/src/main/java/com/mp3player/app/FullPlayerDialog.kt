package com.mp3player.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.mp3player.app.adapter.MusicAdapter
import com.mp3player.app.databinding.DialogFullPlayerBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 全屏播放器弹窗 */
class FullPlayerDialog : DialogFragment() {

    private var _binding: DialogFullPlayerBinding? = null
    private val binding get() = _binding!!

    private var isUserSeeking = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogFullPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnPlayPauseLarge.setOnClickListener {
            (activity as? MainActivity)?.getMusicService()?.playPause()
            updateControls()
        }
        binding.btnNextLarge.setOnClickListener {
            (activity as? MainActivity)?.getMusicService()?.next()
        }
        binding.btnPrevious.setOnClickListener {
            (activity as? MainActivity)?.getMusicService()?.previous()
        }
        binding.btnShuffle.setOnClickListener {
            (activity as? MainActivity)?.getMusicService()?.toggleShuffle()
            updateControls()
        }
        binding.btnRepeat.setOnClickListener {
            (activity as? MainActivity)?.getMusicService()?.cycleRepeatMode()
            updateControls()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = MusicAdapter.formatDuration(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                seekBar?.let {
                    (activity as? MainActivity)?.getMusicService()?.seekTo(it.progress)
                }
            }
        })

        updateTrackInfo()
        updateControls()

        viewLifecycleOwner.lifecycleScope.launch {
            while (_binding != null) {
                updateProgress()
                delay(500)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(MusicPlayerService.ACTION_TRACK_CHANGED)
            addAction(MusicPlayerService.ACTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(playerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(playerReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(playerReceiver)
        } catch (_: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateTrackInfo() {
        val service = (activity as? MainActivity)?.getMusicService() ?: return
        val music = service.getCurrentMusic() ?: return

        binding.tvTitleLarge.text = music.title
        binding.tvArtistLarge.text = music.artist

        if (music.isOnline && !music.imageUrl.isNullOrBlank()) {
            Glide.with(binding.ivAlbumArtLarge.context)
                .load(music.imageUrl)
                .placeholder(R.drawable.ic_music_placeholder)
                .error(R.drawable.ic_music_placeholder)
                .into(binding.ivAlbumArtLarge)
        } else {
            binding.ivAlbumArtLarge.setImageResource(R.drawable.ic_music_placeholder)
        }
    }

    private fun updateControls() {
        val service = (activity as? MainActivity)?.getMusicService() ?: return
        binding.btnPlayPauseLarge.setImageResource(
            if (service.isPlaying()) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.btnShuffle.setColorFilter(
            ContextCompat.getColor(requireContext(), if (service.isShuffle) R.color.primary else R.color.text_secondary)
        )
        binding.btnRepeat.setImageResource(
            when (service.repeatMode) {
                MusicPlayerService.REPEAT_ONE -> R.drawable.ic_repeat_one
                else -> R.drawable.ic_repeat
            }
        )
        binding.btnRepeat.setColorFilter(
            ContextCompat.getColor(
                requireContext(),
                if (service.repeatMode != MusicPlayerService.REPEAT_NONE) R.color.primary else R.color.text_secondary
            )
        )
    }

    private fun updateProgress() {
        val service = (activity as? MainActivity)?.getMusicService() ?: return
        if (isUserSeeking) return

        val duration = service.getDuration()
        val position = service.getCurrentPosition()
        binding.seekBar.max = if (duration > 0) duration else 100
        binding.seekBar.progress = position.coerceAtLeast(0)
        binding.tvCurrentTime.text = MusicAdapter.formatDuration(position.toLong())
        binding.tvTotalTime.text = MusicAdapter.formatDuration(duration.toLong())
    }

    private val playerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MusicPlayerService.ACTION_TRACK_CHANGED -> updateTrackInfo()
                MusicPlayerService.ACTION_STATE_CHANGED -> updateControls()
            }
        }
    }
}
