package com.mp3player.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.mp3player.app.R
import com.mp3player.app.data.Music
import com.mp3player.app.databinding.ItemOnlineMusicBinding

/** 在线音乐列表适配器 */
class OnlineMusicAdapter(
    private val onItemClick: (Int) -> Unit,
    private val onDownloadClick: (Music) -> Unit
) : RecyclerView.Adapter<OnlineMusicAdapter.ViewHolder>() {

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

    inner class ViewHolder(val binding: ItemOnlineMusicBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOnlineMusicBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val music = musicList[position]
        with(holder.binding) {
            tvTitle.text = music.title
            tvArtist.text = music.artist
            tvDuration.text = MusicAdapter.formatDuration(music.duration)

            if (music.imageUrl.isNullOrBlank()) {
                ivCover.setImageResource(R.drawable.ic_music_placeholder)
            } else {
                Glide.with(ivCover.context)
                    .load(music.imageUrl)
                    .placeholder(R.drawable.ic_music_placeholder)
                    .error(R.drawable.ic_music_placeholder)
                    .into(ivCover)
            }

            tvTitle.setTextColor(
                root.context.getColor(
                    if (position == playingIndex) R.color.primary else R.color.text_primary
                )
            )

            root.setOnClickListener { onItemClick(position) }
            btnDownload.setOnClickListener { onDownloadClick(music) }
        }
    }

    override fun getItemCount(): Int = musicList.size
}
