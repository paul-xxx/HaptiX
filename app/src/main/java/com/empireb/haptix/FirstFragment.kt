package com.empireb.haptix

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.empireb.haptix.databinding.FragmentFirstBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private var playerService: HapticPlayerService? = null
    private var isBound = false
    private lateinit var fileAdapter: HapticFileAdapter
    private var currentFiles: List<HapticFile> = emptyList()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HapticPlayerService.LocalBinder
            playerService = binder.getService()
            isBound = true
            setupServiceListeners()
            syncUIWithService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            playerService = null
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            loadFiles()
        } else {
            Toast.makeText(context, "Permissions are required for all features to work", Toast.LENGTH_SHORT).show()
        }
    }

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { startConversion(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        checkHapticSupport()
        setupPlayerControls()
        showStartupWarning()
        
        binding.buttonSelect.setOnClickListener {
            selectFileLauncher.launch("audio/*")
        }

        checkAndRequestPermissions()
        bindPlayerService()
    }

    private fun checkAndRequestPermissions() {
        val neededPermissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            neededPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            neededPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        val toRequest = neededPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            permissionLauncher.launch(toRequest.toTypedArray())
        } else {
            loadFiles()
        }
    }

    private fun bindPlayerService() {
        Intent(requireContext(), HapticPlayerService::class.java).also { intent ->
            requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun setupServiceListeners() {
        playerService?.apply {
            onTrackChanged = { index ->
                if (_binding != null) {
                    binding.playerCard.visibility = View.VISIBLE
                    binding.textviewPlayerTitle.text = playlist[index].name
                    updatePlayPauseIcon()
                }
            }
            onPlaybackStateChanged = { isPlaying ->
                updatePlayPauseIcon()
            }
            onProgressUpdate = { pos, duration ->
                if (_binding != null) {
                    binding.playerSeekbar.max = duration
                    binding.playerSeekbar.progress = pos
                }
            }
        }
    }

    private fun syncUIWithService() {
        playerService?.let { service ->
            if (service.currentIndex >= 0 && service.playlist.isNotEmpty()) {
                binding.playerCard.visibility = View.VISIBLE
                binding.textviewPlayerTitle.text = service.playlist[service.currentIndex].name
                binding.playerSeekbar.max = service.getDuration()
                binding.playerSeekbar.progress = service.getCurrentPosition()
                updatePlayPauseIcon()
            }
        }
    }

    private fun setupPlayerControls() {
        binding.buttonPlayPause.setOnClickListener {
            playerService?.let {
                if (it.isPlaying()) it.pause() else it.play()
            }
        }
        
        binding.buttonNext.setOnClickListener { playerService?.playNext() }
        binding.buttonPrevious.setOnClickListener { playerService?.playPrevious() }
        
        binding.buttonShuffle.setOnClickListener {
            Toast.makeText(context, "Shuffle toggled", Toast.LENGTH_SHORT).show()
        }
        
        binding.buttonRepeat.setOnClickListener {
            Toast.makeText(context, "Repeat toggled", Toast.LENGTH_SHORT).show()
        }

        binding.playerSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) playerService?.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updatePlayPauseIcon() {
        _binding?.let {
            val isPlaying = playerService?.isPlaying() ?: false
            it.buttonPlayPause.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
        }
    }

    private fun showStartupWarning() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.warning_title)
            .setMessage(R.string.warning_message)
            .setPositiveButton(R.string.ok, null)
            .setCancelable(false)
            .show()
    }

    private fun checkHapticSupport() {
        val isSupported = AudioManager.isHapticPlaybackSupported()
        if (!isSupported) {
            Toast.makeText(context, "Haptic playback NOT supported on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupRecyclerView() {
        fileAdapter = HapticFileAdapter(
            onFileClick = { hapticFile ->
                val index = currentFiles.indexOf(hapticFile)
                if (index != -1) {
                    playerService?.playlist = currentFiles
                    playerService?.playFile(index)
                }
            },
            onFileLongClick = { hapticFile ->
                showFileMenu(hapticFile)
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = fileAdapter
        }
    }

    private fun showFileMenu(file: HapticFile) {
        val index = currentFiles.indexOf(file)
        if (index == -1) return
        
        val viewHolder = binding.recyclerView.findViewHolderForAdapterPosition(index)
        val popup = PopupMenu(requireContext(), viewHolder?.itemView ?: binding.recyclerView)
        popup.menuInflater.inflate(R.menu.menu_file_actions, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_play -> {
                    playerService?.playlist = currentFiles
                    playerService?.playFile(index)
                    true
                }
                R.id.action_delete -> {
                    confirmDelete(file)
                    true
                }
                R.id.action_share -> {
                    shareFile(file)
                    true
                }
                R.id.action_rename -> {
                    showRenameDialog(file)
                    true
                }
                R.id.action_info -> {
                    showInfoDialog(file)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun loadFiles() {
        val appContext = context?.applicationContext ?: return
        val contentResolver = appContext.contentResolver
        
        lifecycleScope.launch(Dispatchers.IO) {
            val hapticFiles = mutableListOf<HapticFile>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED
            )
            
            // Более надежный поиск по относительной дорожке
            val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%Haptic Converted%")
            
            try {
                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn)
                        val size = cursor.getLong(sizeColumn)
                        val date = cursor.getLong(dateColumn)
                        val contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                        
                        hapticFiles.add(HapticFile(name, contentUri, size, date))
                    }
                }
            } catch (e: Exception) {
                Log.e("HaptiX", "Error querying MediaStore", e)
            }
            
            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    currentFiles = hapticFiles
                    fileAdapter.submitList(hapticFiles)
                    playerService?.playlist = hapticFiles
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        val ctx = context ?: return "audio.mp3"
        if (uri.scheme == "content") {
            val cursor = ctx.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result ?: "converted_audio.ogg"
    }

    private fun startConversion(inputUri: Uri) {
        val originalName = getFileName(inputUri)
        val outputName = if (originalName.contains(".")) {
            originalName.substringBeforeLast(".") + "_haptic.ogg"
        } else {
            originalName + "_haptic.ogg"
        }

        binding.buttonSelect.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val conversionResult = runConversion(inputUri)
                val success = conversionResult.first
                val file = conversionResult.second
                if (success && file != null) {
                    saveFileToMediaStore(file, outputName)
                } else false
            }
            
            _binding?.let {
                it.buttonSelect.isEnabled = true
                it.progressBar.visibility = View.GONE
                
                if (result) {
                    loadFiles()
                } else {
                    Toast.makeText(requireContext(), R.string.status_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun runConversion(inputUri: Uri): Pair<Boolean, File?> {
        val ctx = context?.applicationContext ?: return Pair(false, null)
        val cacheDir = ctx.cacheDir
        val id = UUID.randomUUID().toString().take(8)
        
        val originalName = getFileName(inputUri)
        val extension = if (originalName.contains(".")) originalName.substringAfterLast(".") else "audio"
        val inputPath = File(cacheDir, "input_$id.$extension").absolutePath
        val lPath = File(cacheDir, "temp_L_$id.wav").absolutePath
        val rPath = File(cacheDir, "temp_R_$id.wav").absolutePath
        val hPath = File(cacheDir, "temp_H_$id.wav").absolutePath
        val tempOutputPath = File(cacheDir, "output_$id.ogg").absolutePath

        try {
            ctx.contentResolver.openInputStream(inputUri)?.use { input ->
                File(inputPath).outputStream().use { output -> input.copyTo(output) }
            } ?: return Pair(false, null)

            FFmpegKit.execute("-i \"$inputPath\" -af \"pan=mono|c0=c0\" \"$lPath\" -y")
            FFmpegKit.execute("-i \"$inputPath\" -af \"pan=mono|c0=c1\" \"$rPath\" -y")
            val subFilter = "pan=mono|c0=0.5*c0+0.5*c1,highpass=f=60,lowpass=f=250,alimiter=limit=1.0"
            FFmpegKit.execute("-i \"$inputPath\" -af \"$subFilter\" \"$hPath\" -y")

            val mergeCmd = "-i \"$lPath\" -i \"$rPath\" -i \"$hPath\" -i \"$inputPath\" " +
                    "-filter_complex \"[0:a][1:a][2:a]amerge=inputs=3[aout]\" " +
                    "-map \"[aout]\" -map_metadata 3 " +
                    "-c:a libopus -mapping_family 255 -b:a 192k " +
                    "-metadata ANDROID_HAPTIC=1 \"$tempOutputPath\" -y"
            
            val session = FFmpegKit.execute(mergeCmd)

            File(inputPath).delete()
            File(lPath).delete()
            File(rPath).delete()
            File(hPath).delete()

            return if (ReturnCode.isSuccess(session.returnCode)) {
                Pair(true, File(tempOutputPath))
            } else {
                Log.e("HaptiX", "FFmpeg failed: ${session.allLogsAsString}")
                Pair(false, null)
            }
        } catch (e: Exception) {
            Log.e("HaptiX", "Error during conversion", e)
            return Pair(false, null)
        }
    }

    private fun saveFileToMediaStore(file: File, fileName: String): Boolean {
        val ctx = context?.applicationContext ?: return false
        val relativePath = Environment.DIRECTORY_MUSIC + File.separator + "Haptic Converted"
        
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/ogg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            file.delete()
            true
        } catch (e: Exception) {
            Log.e("HaptiX", "Error saving to MediaStore", e)
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun confirmDelete(file: HapticFile) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.menu_delete)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.ok) { _, _ -> deleteFile(file) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteFile(file: HapticFile) {
        try {
            requireContext().contentResolver.delete(file.uri, null, null)
            loadFiles()
            Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("HaptiX", "Error deleting file", e)
        }
    }

    private fun shareFile(file: HapticFile) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/ogg"
            putExtra(Intent.EXTRA_STREAM, file.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.menu_share)))
    }

    private fun showRenameDialog(file: HapticFile) {
        val input = EditText(requireContext())
        input.setText(file.name.substringBeforeLast("."))
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rename_title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = input.text.toString() + ".ogg"
                renameFile(file, newName)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renameFile(file: HapticFile, newName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, newName)
        }
        try {
            requireContext().contentResolver.update(file.uri, values, null, null)
            loadFiles()
        } catch (e: Exception) {
            Log.e("HaptiX", "Error renaming file", e)
        }
    }

    private fun showInfoDialog(file: HapticFile) {
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(file.dateModified * 1000))
        val info = "Name: ${file.name}\nSize: ${fileAdapter.formatFileSize(file.size)}\nModified: $dateStr\nURI: ${file.uri}"
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.menu_info)
            .setMessage(info)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isBound) {
            requireContext().unbindService(connection)
            isBound = false
        }
        _binding = null
    }
}
