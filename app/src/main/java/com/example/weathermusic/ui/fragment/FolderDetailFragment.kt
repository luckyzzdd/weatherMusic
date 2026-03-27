package com.example.weathermusic.ui.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.weathermusic.data.local.Music
import com.example.weathermusic.data.repository.MusicRepository
import com.example.weathermusic.databinding.FragmentFolderDetailBinding
import com.example.weathermusic.service.MusicPlayService
import com.example.weathermusic.ui.adapter.MusicListAdapter
import com.example.weathermusic.ui.adapter.OnMusicItemListener
import com.example.weathermusic.ui.view.SimpleCircleView
import com.example.weathermusic.utils.MusicPlayerManager
import com.example.weathermusic.utils.OnPlayStateChangeListener
import com.example.weathermusic.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

class FolderDetailFragment : Fragment(), OnMusicItemListener {
    private lateinit var adapter: MusicListAdapter
    private lateinit var binding: FragmentFolderDetailBinding
    private lateinit var musicViewModel: MusicViewModel
    private var folderId = 0
    private var folderName = ""

    // 保存监听器实例，用于销毁时移除
    private lateinit var playStateListener: OnPlayStateChangeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            folderId = it.getInt("folder_id")
            folderName = it.getString("folder_name", "")
        }
        musicViewModel = ViewModelProvider(requireActivity())[MusicViewModel::class.java]
        adapter = MusicListAdapter(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFolderDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        loadSongs()
        updateListAdapter()
        observeProgress()
        // 播放模式切换监听 + Toast提示
        binding.circleView.onModeChanged = { mode ->
            // 1. 设置播放模式
            MusicPlayerManager.setMode(mode)
            // 2. 弹出对应Toast提示
            val tip = when (mode) {
                SimpleCircleView.Mode.LIST_LOOP -> "列表循环"
                SimpleCircleView.Mode.RANDOM -> "随机播放"
                SimpleCircleView.Mode.SINGLE_LOOP -> "单曲循环"
            }
            // 弹Toast（Fragment中用 requireContext()）
            android.widget.Toast.makeText(requireContext(), tip, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateListAdapter() {
        playStateListener = object: OnPlayStateChangeListener{
            override fun onPlayStateChanged(isPlaying: Boolean, currentMusic: Music?) {
                adapter.updatePlayState(currentMusic?.path, isPlaying)
            }
            override fun onMusicChanged(newMusic: Music?) {}
            override fun onPlayListEnd() {
                //此时关闭Manager
                MusicPlayerManager.stopPlay()
                //刷新ui
                adapter.updatePlayState(null,false)
            }
        }
        // 注册监听器
        MusicPlayerManager.addOnPlayStateChangeListener(playStateListener)
    }



    private fun initView() {
        binding.tvFolderName.text = folderName
        binding.rvFolderSongs.layoutManager = LinearLayoutManager(context)
        binding.rvFolderSongs.adapter = adapter
        binding.ivBack.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }
    }

    private fun observeProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
            MusicPlayerManager.progressFlow.collect { (pos, dur) ->
                adapter.notifyItemChanged(adapter.currentList.indexOfFirst { it.path == MusicPlayerManager.getCurrentMusic()?.path })
            }
        }
    }

    private fun loadSongs() {
        musicViewModel.viewModelScope.launch {
            val songs = MusicRepository.getMusicsByFolderIdDirectly(folderId)
            adapter.submitList(songs)
            val isInFolder: Boolean = songs.contains(MusicPlayerManager.getCurrentMusic())
            if (isInFolder)   adapter.updatePlayState(MusicPlayerManager.getCurrentMusic()?.path,
                MusicPlayerManager.isPlaying())
            binding.tvEmptyTip.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
            MusicPlayerManager.setPlayList(songs)
        }
    }

    override fun onMusicPlayClick(music: Music) {
        if (MusicPlayerManager.getCurrentMusic()?.path == music.path) {
            if (MusicPlayerManager.isPlaying()) MusicPlayService.pausePlay(requireContext())
            else MusicPlayService.resumePlay(requireContext())
        } else {
            MusicPlayService.startPlay(requireContext(), music)
        }
    }

    override fun onMusicRemoveClick(music: Music) {
        AlertDialog.Builder(requireContext())
            .setTitle("移出歌曲")
            .setMessage("确定移出？")
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    MusicRepository.assignMusicToFolder(music.path, null)
                    loadSongs()
                    Toast.makeText(context, "已移出", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter.updatePlayState(null, false)
        // 销毁时移除监听器，防止内存泄漏
        MusicPlayerManager.removeOnPlayStateChangeListener(playStateListener)
    }

    companion object {
        fun newInstance(folderId: Int, folderName: String) = FolderDetailFragment().apply {
            arguments = Bundle().apply {
                putInt("folder_id", folderId)
                putString("folder_name", folderName)
            }
        }
    }
}