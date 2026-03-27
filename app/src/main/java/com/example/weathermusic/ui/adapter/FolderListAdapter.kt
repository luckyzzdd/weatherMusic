package com.example.weathermusic.ui.adapter


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.RestrictTo
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.weathermusic.data.local.MusicFolder
import com.example.weathermusic.data.repository.MusicRepository
import com.example.weathermusic.databinding.FragmentCityBinding
import com.example.weathermusic.databinding.ItemMusicFolderBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 收藏夹列表Adapter（绑定3个收藏夹卡片）
 */
class FolderListAdapter(
    private val onManualAddClick: (Int, String) -> Unit,
    private val onFolderClick: (MusicFolder) -> Unit  // 新增：收藏夹卡片点击回调
) : ListAdapter<MusicFolder, FolderListAdapter.FolderViewHolder>(FolderItemCallback()) {
    private lateinit var binding: ItemMusicFolderBinding
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): FolderViewHolder {
       binding = ItemMusicFolderBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: FolderViewHolder,
        position: Int
    ) {
        val folder = getItem(position)
        holder.bind(folder)
        holder.itemView.setOnClickListener {
            onFolderClick(folder)
        }
    }

    inner class FolderViewHolder(private val binding: ItemMusicFolderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(folder: MusicFolder) {
            // 绑定收藏夹名称
            binding.tvFolderName.text = "${folder.folderName}收藏夹"
            // 临时显示“已添加0首”（后续改查数据库）
            binding.tvSongCount.text = "已添加0首"

            CoroutineScope(Dispatchers.IO).launch{
                // 改为监听Flow（需MusicRepository新增返回Flow的方法）
                MusicRepository.observeSongCountByFolderIdFlow(folder.folderId).collect { count ->
                    withContext(Dispatchers.Main) {
                        binding.tvSongCount.text = "已添加${count}首"
                    }
                }
            }
            // 手动添加按钮点击事件：回调传收藏夹ID（关键，绑定到对应收藏夹）
            binding.btnAddManual.setOnClickListener {
                onManualAddClick.invoke(folder.folderId,folder.folderName)
            }


            // 1. 卡片主体点击 → 跳详情页
            binding.root.setOnClickListener {
                onFolderClick.invoke(folder) // 传整个MusicFolder对象，方便拿id/name
            }

        }
    }
}

class FolderItemCallback : DiffUtil.ItemCallback<MusicFolder>() {
    override fun areItemsTheSame(
        oldItem: MusicFolder,
        newItem: MusicFolder
    ): Boolean {
        return oldItem.folderId == newItem.folderId
    }

    override fun areContentsTheSame(
        oldItem: MusicFolder,
        newItem: MusicFolder
    ): Boolean {
        return oldItem == newItem
    }

}