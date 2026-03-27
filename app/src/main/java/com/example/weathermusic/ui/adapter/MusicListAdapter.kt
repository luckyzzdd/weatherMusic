package com.example.weathermusic.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.weathermusic.R
import com.example.weathermusic.data.local.Music
import com.example.weathermusic.databinding.ItemMusicBinding
import com.example.weathermusic.utils.MusicPlayerManager
import com.example.weathermusic.utils.MusicScanner

interface OnMusicItemListener {
    fun onMusicPlayClick(music: Music)
    fun onMusicRemoveClick(music: Music)
}

class MusicListAdapter(
    private val listener: OnMusicItemListener,
    private val showRemove: Boolean = false,
) : ListAdapter<Music, MusicListAdapter.MusicViewHolder>(MusicDiffCallback()) {

    private var currentPlayingPath: String? = null
    private var isPlaying = false

    // 更新播放状态（无报错）
    // 传入两个参数：当前播放的音乐路径 path，播放状态 playing（true=播放，false=暂停）
    fun updatePlayState(path: String?, playing: Boolean) {
        // 1. 找到【上一首正在播放的音乐】在列表里的位置
        val oldPos = currentList.indexOfFirst { it.path == currentPlayingPath }
        // 2. 找到【现在要播放的音乐】在列表里的位置
        val newPos = currentList.indexOfFirst { it.path == path }

        // 3. 更新适配器内部记录的：当前播放音乐路径 + 播放状态
        currentPlayingPath = path
        isPlaying = playing

        // 4. 如果上一首有播放的，刷新这一行（让它变回暂停状态）
        if (oldPos != -1) notifyItemChanged(oldPos)
        // 5. 如果新的音乐有效，刷新这一行（让它显示播放状态/动画/进度条）
        if (newPos != -1) notifyItemChanged(newPos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val binding = ItemMusicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MusicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MusicViewHolder(val binding: ItemMusicBinding) : RecyclerView.ViewHolder(binding.root) {
        // 修复：动画移到ViewHolder加载，杜绝空指针
        private val pulseAnim = AnimationUtils.loadAnimation(itemView.context, R.anim.anim_play_pulse)


        fun bind(music: Music) {
            binding.tvMusicName.text = music.name
            binding.tvMusicSinger.text = music.singer
            binding.tvMusicDuration.text = MusicScanner.formatDuration(music.duration)

            val isCurrent = music.path == currentPlayingPath
            binding.sbMusicProgress.visibility = if (isCurrent) View.VISIBLE else View.GONE

            if (isCurrent) {
                // 同步进度条
                binding.sbMusicProgress.max = MusicPlayerManager.getCurrentDuration()
                binding.sbMusicProgress.progress = MusicPlayerManager.getCurrentPosition()
                // 仅播放中的Item设置监听
                binding.sbMusicProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        // ✅ 双重判空：只有播放中、用户手动拖动才执行
                        if (fromUser&& MusicPlayerManager.getCurrentMusic() != null) {
                            MusicPlayerManager.seekTo(progress)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })

                // 修复：使用你项目原有的图片名，无资源报错
                if (isPlaying) {
                    binding.ivPlay.setImageResource(R.drawable.play)
                    binding.ivPlay.startAnimation(pulseAnim)
                } else {
                    binding.ivPlay.setImageResource(R.drawable.pause)
                    binding.ivPlay.clearAnimation()
                }
            } else {
                binding.ivPlay.setImageResource(R.drawable.pause)
                binding.ivPlay.clearAnimation()
            }

            // 点击事件
            binding.root.setOnClickListener { listener.onMusicPlayClick(music) }
            binding.root.setOnLongClickListener {
                listener.onMusicRemoveClick(music)
                true
            }
        }
    }
}

// Diff工具类（无报错）
class MusicDiffCallback : DiffUtil.ItemCallback<Music>() {
    override fun areItemsTheSame(oldItem: Music, newItem: Music) = oldItem.path == newItem.path
    override fun areContentsTheSame(oldItem: Music, newItem: Music) = oldItem == newItem
}