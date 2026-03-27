package com.example.weathermusic.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.weathermusic.data.local.Music
import com.example.weathermusic.databinding.ItemSongSelectBinding
import com.google.android.material.checkbox.MaterialCheckBox

//// 歌曲选择Adapter（带质感圆形勾选框）
/**
 * submitList(availableSongs) 把歌曲列表传给了 ListAdapter 父类内置的、隐藏的数据源
 * 继承了 ListAdapter<Music, ...>，ListAdapter 是 Android 封装的 “智能列表适配器”，核心设计是：
 */
class SongSelectAdapter(private val onCheckChanged: (Music, Boolean)-> Unit,

): ListAdapter<Music, SongSelectAdapter.ViewHolder>(DiffCallback()) {
    // 记录选中的歌曲
    private val selectedSongs = mutableListOf<Music>()
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemSongSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
      holder.bind(getItem(position))
    }
    // 更新选中的歌曲列表
    fun updateSelectedSongs(song: Music, isChecked: Boolean) {
        if (isChecked) {
            if (!selectedSongs.contains(song)) selectedSongs.add(song)
        } else {
            selectedSongs.remove(song)
        }
    }

    // 获取所有选中的歌曲
    fun getSelectedSongs() = selectedSongs.toList()
    inner class ViewHolder(private var binding: ItemSongSelectBinding): RecyclerView.ViewHolder(binding.root){
        //绑定歌曲信息和勾选框
        fun bind(music: Music){
            binding.tvSongName.text = music.name
            binding.tvSinger.text = music.singer

            val checkBox = binding.cbSelect as MaterialCheckBox
            //「在当前弹窗生命周期内，保持勾选状态的临时记忆」
            checkBox.isChecked = selectedSongs.contains(music)
            checkBox.setOnCheckedChangeListener { button,isChecked ->
                onCheckChanged(music, isChecked)
            }
        }
    }
}
// DiffUtil（复用你已有的，提升列表性能）
class DiffCallback : DiffUtil.ItemCallback<Music>() {
    override fun areItemsTheSame(oldItem: Music, newItem: Music): Boolean {
        return oldItem.path == newItem.path
    }

    override fun areContentsTheSame(oldItem: Music, newItem: Music): Boolean {
        return oldItem == newItem
    }
}