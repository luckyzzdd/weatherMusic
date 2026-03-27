package com.example.weathermusic.ui.dialog

import android.app.AlertDialog
import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.weathermusic.data.repository.MusicRepository
import com.example.weathermusic.databinding.DialogLoadingBinding
import com.example.weathermusic.databinding.DialogSongSelectorBinding
import com.example.weathermusic.ui.adapter.SongSelectAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log

class SongSelectorBottomSheet : BottomSheetDialogFragment() {
    private lateinit var binding: DialogSongSelectorBinding
    private lateinit var songAdapter: SongSelectAdapter
    private var folderId: Int = 0

    // 加载弹窗（AI分拣时显示）
    private var loadingDialog: AlertDialog? = null

    companion object {
        fun instance(folderId: Int?, folderName: String?): SongSelectorBottomSheet {
            val fragment = SongSelectorBottomSheet()
            val args = Bundle()
            folderId?.let { args.putInt("folderId", it) }
            args.putString("folderName", folderName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DialogSongSelectorBinding.inflate(inflater, container, false)
        folderId = arguments?.getInt("folderId") ?: 0
        val folderName = arguments?.getString("folderName") ?: "ai"
        binding.tvTitle.text = "添加歌曲到【$folderName】"
        if (folderName == "ai") {
            binding.tvTitle.text = "添加歌曲让【ai】分拣"
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initSongList()
        bindTopBarClick()
        loadUnassignedSongs() // 替换原loadLocalSongs
    }

    // 核心修改：只加载未归属任何收藏夹的歌曲（彻底解决重复问题）
    private fun loadUnassignedSongs() {
        lifecycleScope.launch {
            val unassignedSongs = MusicRepository.getUnassignedMusics()
            songAdapter.submitList(unassignedSongs)
            // 无未归属歌曲时的提示
            if (unassignedSongs.isEmpty()) {
                Toast.makeText(requireContext(), "所有歌曲已归属收藏夹", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    private fun showLoadingDialog() {
        // 初始化加载弹窗布局
        val loadingBinding = DialogLoadingBinding.inflate(layoutInflater)
        loadingBinding.tvLoadingMsg.text = "正在分析歌曲情感，匹配收藏夹..."
        // 构建AlertDialog
        loadingDialog = AlertDialog.Builder(requireContext())
            .setView(loadingBinding.root)
            .setCancelable(false) // 不可取消
            .create()
        // 显示弹窗（设置背景透明，避免默认白色边框）
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()
    }

    // 新增：关闭加载弹窗
    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun bindTopBarClick() {
        binding.tvCancel.setOnClickListener { dismiss() }

        binding.tvConfirm.setOnClickListener {
            val selectedSongs = songAdapter.getSelectedSongs()
            if (selectedSongs.isEmpty()) {
                Toast.makeText(requireContext(), "请至少选择一首歌曲", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (folderId != 0) {// 批量更新歌曲的folderId（多对一核心逻辑）
                lifecycleScope.launch(Dispatchers.IO) {
                    selectedSongs.forEach { song ->
                        Log.d("SongSelector", song.toString())
                        Log.d("SongSelector", "id" + folderId.toString())
                        //这里是正式的在数据库里改folderId
                        MusicRepository.assignMusicToFolder(song.path, folderId)
                    }
                    // 切回主线程提示
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "成功添加${selectedSongs.size}首歌曲",
                            Toast.LENGTH_SHORT
                        ).show()
                        dismiss()
                    }
                }
            } else {
                // AI分拣
                // 显示自定义加载弹窗
                showLoadingDialog()
                // 调用AI分拣（IO线程）
                lifecycleScope.launch(Dispatchers.IO) {
                    var successCount = 0
                    var failCount = 0
                    //获得所有收藏夹列表
                    val folderNames= MusicRepository.getAllFolderNames()
                    // 遍历选中的歌曲调用AI
                    selectedSongs.forEach { song ->
                        try {// 调用AI获取匹配的收藏夹名称
                            val matchFolderName = MusicRepository.aiClassifySingleSong(song, folderNames)
                            if (matchFolderName!=null){
                                val folderIdByName = MusicRepository.getFolderIdByName(matchFolderName)
                                //更新歌曲的folderId
                                MusicRepository.assignMusicToFolder(song.path,folderIdByName)
                                successCount++
                            }else{
                                failCount++
                                Log.e("AI分类", "歌曲《${song.name}》未匹配到任何收藏夹")
                            }
                        } catch (e: Exception) {
                            failCount++
                            Log.e("AI分类", "歌曲《${song.name}》分类失败：${e.message}", e)
                        }
                    }
                    // 切回主线程处理结果
                    withContext(Dispatchers.Main) {
                        // 关闭加载弹窗
                        dismissLoadingDialog()
                        // 显示分拣结果
                        val toastMsg = when {
                            successCount == selectedSongs.size -> "AI分拣完成：全部$successCount 首歌曲匹配成功✅"
                            successCount > 0 -> "AI分拣完成：成功$successCount 首，失败$failCount 首（失败歌曲可手动分配）"
                            else -> "AI分拣失败：$failCount 首歌曲未匹配到收藏夹❌"
                        }
                        Toast.makeText(requireContext(), toastMsg, Toast.LENGTH_LONG).show()
                        // 关闭半模态框
                        dismiss()
                    }

                }
            }
        }
    }

    private fun initSongList() {
        songAdapter = SongSelectAdapter { song, isChecked ->
            songAdapter.updateSelectedSongs(song, isChecked)
        }
        binding.rvSongList.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }
    }
}