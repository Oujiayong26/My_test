package com.mp3player.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mp3player.app.R
import com.mp3player.app.data.Music
import com.mp3player.app.databinding.ItemMusicBinding

/** 本地音乐列表适配器 */
class MusicAdapter(
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<MusicAdapter.ViewHolder>() {

    private var musicList: List<Music> = emptyList()
    private var playingIndex = -1

    fun getMusicList(): List<Music> = musicList

    fun submitList(list: List<Music>) {
        musicList = list
        notifyDataSetChanged()
    }

    fun setPlayingIndex(index: Int) {
        val old = playingIndex
        playingIndex = index
        if (old in musicList.indices) notifyItemChanged(old)
        if (index in musicList.indices) notifyItemChanged(index)
    }

    inner class ViewHolder(val binding: ItemMusicBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMusicBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val music = musicList[position]
        with(holder.binding) {
            tvNumber.text = (position + 1).toString()
            tvTitle.text = music.title
            tvSubtitle.text = "${music.artist} - ${music.album}"
            tvDuration.text = formatDuration(music.duration)

            val isPlaying = position == playingIndex
            tvTitle.setTextColor(
                root.context.getColor(
                    if (isPlaying) R.color.primary else R.color.text_primary
                )
            )
            root.setBackgroundColor(
                root.context.getColor(
                    if (isPlaying) R.color.surface else android.R.color.transparent
                )
            )

            root.setOnClickListener { onItemClick(position) }
        }
    }

    override fun getItemCount(): Int = musicList.size

    companion object {
        fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}
