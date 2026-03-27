package com.example.weathermusic.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.weathermusic.R
import com.example.weathermusic.databinding.FragmentMusicBinding
import com.example.weathermusic.ui.adapter.FolderListAdapter
import com.example.weathermusic.ui.dialog.AddFolderDialog
import com.example.weathermusic.ui.dialog.SongSelectorBottomSheet
import com.example.weathermusic.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

class MusicFragment : Fragment() {
    private lateinit var binding: FragmentMusicBinding
    private val TAG = "MusicFragment"
    private lateinit var musicViewModel: MusicViewModel
    private lateinit var folderListAdapter: FolderListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        musicViewModel = ViewModelProvider(requireActivity())[MusicViewModel::class.java]

        // 删除：权限Launcher初始化、权限检查调用
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMusicBinding.inflate(inflater, container, false)
        initView()
        initSwipeRefresh()
        initFolderRecyclerView()
        observeMusicFolderList()

        return binding.root
    }

    // 下拉刷新（删除权限检查，直接扫描）
    private fun initSwipeRefresh() {
        binding.srlRefresh.setColorSchemeResources(R.color.pink_light)
        binding.srlRefresh.setOnRefreshListener {
            // 直接扫描，权限由WeatherFragment统一处理
            scanAndSaveLocalMusicReFresh()
        }
    }

    private fun initView() {
        bindAiAddButton()
        bindAddFolderIconClick()
    }

    // 添加文件夹按钮点击
    private fun bindAddFolderIconClick() {
        binding.ivAddFolder.setOnClickListener {
            AddFolderDialog().show(childFragmentManager, "AddFolderDialog")
        }
    }

    // 观察收藏夹列表
    private fun observeMusicFolderList() {
        musicViewModel.musicFolderList.observe(viewLifecycleOwner) { folders ->
            folderListAdapter.submitList(folders)
        }
    }

    // AI添加按钮（占位）
    private fun bindAiAddButton() {
        binding.btnAddAi.setOnClickListener {
            val sheet = SongSelectorBottomSheet.instance(null, null)
            sheet.show(childFragmentManager, "SongSelectorBottomSheet")
        }
    }

    // 初始化收藏夹列表RecyclerView
    private fun initFolderRecyclerView() {
        folderListAdapter = FolderListAdapter(
            onManualAddClick = { folderId, folderName ->
                val sheet = SongSelectorBottomSheet.instance(folderId, folderName)
                sheet.show(childFragmentManager, "SongSelectorBottomSheet")
            },
            onFolderClick = { musicFolder ->
                val detailFragment = FolderDetailFragment.newInstance(
                    folderId = musicFolder.folderId,
                    folderName = musicFolder.folderName
                )

                //replace
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, detailFragment)
                    .addToBackStack("FolderDetail")
                    .commit()
            }
        )
        binding.rvFolderList.apply {
            adapter = folderListAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    // 扫描本地音乐（无权限检查，权限由WeatherFragment统一处理）
    private fun scanAndSaveLocalMusic() {
        musicViewModel.scanAndSaveLocalMusic()
        Toast.makeText(requireContext(), "开始扫描并存储音乐...", Toast.LENGTH_SHORT).show()
    }

    // 带刷新动画的扫描逻辑
    private fun scanAndSaveLocalMusicReFresh() {
        lifecycleScope.launch {
            try {
                binding.srlRefresh.isRefreshing = true
                musicViewModel.scanAndSaveLocalMusic()
                Toast.makeText(requireContext(), "扫描完成！", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "扫描音乐失败", e)
                Toast.makeText(requireContext(), "扫描失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.srlRefresh.isRefreshing = false
            }
        }
    }

    // 删除：onDestroy里的权限Launcher注销（已无相关代码）
    override fun onDestroy() {
        super.onDestroy()
    }
}