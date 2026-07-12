package com.dezzmusic.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.dezzmusic.R
import com.dezzmusic.databinding.FragmentHomeBinding
import com.dezzmusic.db.Song
import com.dezzmusic.telegram.InlineMusicResult
import com.dezzmusic.telegram.TelegramManager
import com.dezzmusic.MusicRepository
import com.dezzmusic.music.MusicService
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.*

private val coverOptions = RequestOptions().transform(RoundedCorners(12)).placeholder(R.drawable.ic_music_note).error(R.drawable.ic_music_note)

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchAdapter: InlineSearchResultAdapter
    private lateinit var recentSearchesAdapter: RecentSearchAdapter
    private lateinit var trendingAdapter: TrendingAdapter
    private lateinit var recentSearchesChipAdapter: RecentSearchesChipAdapter

    private var searchJob: Job? = null
    private var currentQuery = ""
    private val recentSearches = mutableListOf<String>()
    private val recentSearchesLimit = 10

    private lateinit var miniPlayerBehavior: BottomSheetBehavior<View>
    private var isMiniPlayerExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSearchBar()
        setupRecentSearches()
        setupTrending()
        setupInlineSearchResults()
        setupMiniPlayer()
        loadRecentSearches()
        updateGreeting()
        observeMusicService()
    }

    private fun setupSearchBar() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                currentQuery = query
                onQueryChanged(query)
            }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                    saveRecentSearch(query)
                    hideKeyboard()
                }
                true
            } else false
        })

        binding.tilSearch.setEndIconOnClickListener {
            binding.etSearch.setText("")
        }

        binding.tvClearRecent.setOnClickListener {
            clearRecentSearches()
        }
    }

    private fun onQueryChanged(query: String) {
        searchJob?.cancel()

        if (query.length >= 2) {
            binding.llHomeContent.isVisible = false
            binding.llSearchResultsSection.isVisible = true
            binding.tvSearchResultsTitle.text = "Buscando \"$query\"..."

            searchJob = lifecycleScope.launch {
                delay(300) // Debounce
                if (!isAdded) return@launch
                performInlineSearch(query)
            }
        } else {
            binding.llSearchResultsSection.isVisible = false
            binding.llHomeContent.isVisible = true
            binding.rvRecentSearches.visibility = if (recentSearches.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun performSearch(query: String) {
        binding.llHomeContent.isVisible = false
        binding.llSearchResultsSection.isVisible = true
        binding.tvSearchResultsTitle.text = "Resultados para \"$query\""
        binding.rvRecentSearches.visibility = View.GONE

        searchJob = lifecycleScope.launch {
            val results = TelegramManager.getInstance(requireContext()).searchMusic(query)
            if (isAdded) {
                val inlineResults = results.map { r ->
                    InlineMusicResult(
                        id = r.fileId,
                        title = r.title,
                        artist = r.artist,
                        duration = r.duration,
                        audioUrl = "",
                        fileId = r.fileId,
                        thumbnailUrl = r.albumArt,
                        albumArtLocalPath = r.albumArt
                    )
                }
                searchAdapter.submitList(inlineResults)
                binding.tvSearchResultsTitle.text = if (inlineResults.isEmpty()) "No se encontraron resultados para \"$query\"" else "Resultados para \"$query\" (${inlineResults.size})"
            }
        }
    }

    private fun performInlineSearch(query: String) {
        searchJob = lifecycleScope.launch {
            val results = TelegramManager.getInstance(requireContext()).searchMusicInline(query)
            if (isAdded) {
                searchAdapter.submitList(results)
                binding.tvSearchResultsTitle.text = if (results.isEmpty()) "No se encontraron resultados para \"$query\"" else "Resultados para \"$query\" (${results.size})"
            }
        }
    }

    private fun setupRecentSearches() {
        recentSearchesChipAdapter = RecentSearchesChipAdapter(recentSearches) { query ->
            binding.etSearch.setText(query)
            performSearch(query)
        }
        binding.rvRecentSearches.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecentSearches.adapter = recentSearchesChipAdapter

        recentSearchesAdapter = RecentSearchAdapter(
            onQueryClick = { query ->
                binding.etSearch.setText(query)
                performSearch(query)
            },
            onQueryDelete = { query ->
                removeRecentSearch(query)
            }
        )
        binding.rvRecentSearchesGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvRecentSearchesGrid.adapter = recentSearchesAdapter
    }

    private fun setupTrending() {
        trendingAdapter = TrendingAdapter { song ->
            playSong(song)
        }
        binding.rvTrending.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvTrending.adapter = trendingAdapter

        loadTrending()
    }

    private fun setupInlineSearchResults() {
        searchAdapter = InlineSearchResultAdapter(
            onItemClick = { result ->
                playOrDownloadResult(result)
            },
            onDownloadClick = { result ->
                downloadResult(result)
            }
        )

        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchResults.adapter = searchAdapter
    }

    private fun setupMiniPlayer() {
        miniPlayerBehavior = BottomSheetBehavior.from(binding.miniPlayerContainer)
        miniPlayerBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        miniPlayerBehavior.peekHeight = 88
        miniPlayerBehavior.isHideable = true

        miniPlayerBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        isMiniPlayerExpanded = true
                        binding.btnMiniExpand.setImageResource(R.drawable.ic_chevron_down)
                        binding.btnMiniExpand.rotation = 0f
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        isMiniPlayerExpanded = false
                        binding.btnMiniExpand.setImageResource(R.drawable.ic_chevron_up)
                        binding.btnMiniExpand.rotation = 180f
                    }
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        isMiniPlayerExpanded = false
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        binding.miniPlayerContent.setOnClickListener {
            expandPlayer()
        }

        binding.btnMiniPlayPause.setOnClickListener {
            togglePlayPause()
        }

        binding.btnMiniNext.setOnClickListener {
            playNext()
        }
    }

    private fun showMiniPlayer(song: Song, isPlaying: Boolean) {
        binding.miniPlayerContainer.visibility = View.VISIBLE
        miniPlayerBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        Glide.with(this)
            .load(song.albumArt ?: R.drawable.ic_music_note)
            .apply(coverOptions)
            .into(binding.ivMiniCover)

        binding.tvMiniTitle.text = song.title
        binding.tvMiniArtist.text = song.artist
        binding.btnMiniPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

        if (isPlaying) {
            binding.miniPlayerProgress.visibility = View.VISIBLE
            animateProgress()
        } else {
            binding.miniPlayerProgress.visibility = View.GONE
        }
    }

    private fun hideMiniPlayer() {
        miniPlayerBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun expandMiniPlayer() {
        isMiniPlayerExpanded = true
        val intent = android.content.Intent(requireContext(), PlayerActivity::class.java)
        intent.putExtra("song_id", getCurrentSongId())
        startActivity(intent)
        requireActivity().overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top)
    }

    private fun getCurrentSongId(): Long {
        return 0L // TODO: Get actual current song ID from MusicService
    }

    private fun animateProgress() {
        binding.miniPlayerProgress.animate()
            .scaleX(1f)
            .setDuration(10000)
            .start()
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Buenos días"
            hour < 18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        binding.tvGreeting.text = greeting
    }

    private fun loadRecentSearches() {
        val prefs = requireContext().getSharedPreferences("search_prefs", 0)
        val json = prefs.getString("recent_searches", "[]") ?: "[]"
        val list = com.google.gson.Gson().fromJson(json, Array<String>::class.java).toMutableList()
        recentSearches.clear()
        recentSearches.addAll(list)
        updateRecentSearchesUI()
    }

    private fun saveRecentSearch(query: String) {
        recentSearches.remove(query)
        recentSearches.add(0, query)
        if (recentSearches.size > recentSearchesLimit) {
            recentSearches.removeAt(recentSearches.size - 1)
        }
        updateRecentSearchesUI()
        persistRecentSearches()
    }

    private fun removeRecentSearch(query: String) {
        recentSearches.remove(query)
        updateRecentSearchesUI()
        persistRecentSearches()
    }

    private fun clearRecentSearches() {
        recentSearches.clear()
        updateRecentSearchesUI()
        persistRecentSearches()
        Snackbar.make(binding.root, "Historial de búsquedas limpiado", Snackbar.LENGTH_SHORT).show()
    }

    private fun updateRecentSearchesUI() {
        recentSearchesChipAdapter.notifyDataSetChanged()
        recentSearchesAdapter.notifyDataSetChanged()
        binding.llRecentSearchesSection.visibility = if (recentSearches.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvRecentSearchesGrid.visibility = if (recentSearches.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvRecentSearches.visibility = if (recentSearches.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun persistRecentSearches() {
        val prefs = requireContext().getSharedPreferences("search_prefs", 0)
        prefs.edit().putString("recent_searches", com.google.gson.Gson().toJson(recentSearches)).apply()
    }

    private fun loadTrending() {
        lifecycleScope.launch {
            val songs = MusicRepository.getInstance(requireContext()).getMostPlayed(10)
            if (isAdded) {
                trendingAdapter.submitList(songs)
                binding.llTrendingSection.visibility = if (songs.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun playOrDownloadResult(result: InlineMusicResult) {
        val song = Song(
            id = System.currentTimeMillis(),
            title = result.title,
            artist = result.artist,
            duration = result.duration,
            path = result.audioUrl,
            albumArt = result.thumbnailUrl,
            telegramFileId = result.fileId,
            chatId = 0,
            messageId = 0,
            isDownloaded = false
        )
        playSong(song)
    }

    private fun downloadResult(result: InlineMusicResult) {
        val song = Song(
            id = System.currentTimeMillis(),
            title = result.title,
            artist = result.artist,
            duration = result.duration,
            path = "",
            albumArt = result.thumbnailUrl,
            telegramFileId = result.fileId,
            chatId = 0,
            messageId = 0,
            isDownloaded = false
        )

        lifecycleScope.launch {
            val success = TelegramManager.getInstance(requireContext()).downloadSong(song)
            if (success) {
                MusicRepository.getInstance(requireContext()).insertSong(song.copy(isDownloaded = true))
                Snackbar.make(binding.root, "Descargado: ${song.title}", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, "Error al descargar", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun playSong(song: Song) {
        val serviceIntent = android.content.Intent(requireContext(), MusicService::class.java)
        serviceIntent.putExtra("song", song)
        requireContext().startForegroundService(serviceIntent)

        showMiniPlayer(song, true)
    }

    private fun togglePlayPause() {
        val intent = android.content.Intent("com.dezzmusic.PLAY_PAUSE")
        requireContext().sendBroadcast(intent)
    }

    private fun playNext() {
        val intent = android.content.Intent("com.dezzmusic.NEXT")
        requireContext().sendBroadcast(intent)
    }

    private fun hideKeyboard() {
        binding.etSearch.clearFocus()
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun observeMusicService() {
        // TODO: Observe MusicService state via LiveData/Flow
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}

// ===== Adapters =====

class InlineSearchResultAdapter(
    private val onItemClick: (InlineMusicResult) -> Unit,
    private val onDownloadClick: (InlineMusicResult) -> Unit
) : RecyclerView.Adapter<InlineSearchResultAdapter.ViewHolder>() {

    private var items: List<InlineMusicResult> = emptyList()

    fun submitList(newItems: List<InlineMusicResult>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result_inline, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivCover: ImageView = view.findViewById(R.id.ivResultCover)
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvArtist: TextView = view.findViewById(R.id.tvArtist)
        private val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        private val btnDownload: ImageView = view.findViewById(R.id.btnDownload)

        fun bind(result: InlineMusicResult) {
            tvTitle.text = result.title
            tvArtist.text = result.artist
            tvDuration.text = formatDuration(result.duration)

            Glide.with(itemView.context)
                .load(result.thumbnailUrl ?: R.drawable.ic_music_note)
                .apply(coverOptions)
                .into(ivCover)

            itemView.setOnClickListener { onItemClick(result) }
            btnDownload.setOnClickListener { onDownloadClick(result) }
        }

        private fun formatDuration(durationMs: Long): String {
            val minutes = durationMs / 60000
            val seconds = (durationMs % 60000) / 1000
            return String.format("%d:%02d", minutes, seconds)
        }
    }
}

class RecentSearchAdapter(
    private val onQueryClick: (String) -> Unit,
    private val onQueryDelete: (String) -> Unit
) : RecyclerView.Adapter<RecentSearchAdapter.ViewHolder>() {

    private var items: List<String> = emptyList()

    fun submitList(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvQuery: TextView = view.findViewById(R.id.tvQuery)
        private val btnDelete: ImageView = view.findViewById(R.id.btnDelete)

        fun bind(query: String) {
            tvQuery.text = query
            itemView.setOnClickListener { onQueryClick(query) }
            btnDelete.setOnClickListener { onQueryDelete(query) }
        }
    }
}

class RecentSearchesChipAdapter(
    private val items: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<RecentSearchesChipAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val chip = com.google.android.material.chip.Chip(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT, RecyclerView.LayoutParams.WRAP_CONTENT)
            isClickable = true
            setChipCornerRadius(resources.getDimension(R.dimen.chip_corner_radius))
            chipBackgroundColor = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.background_card))
            setTextColor(android.content.res.ColorStateList.valueOf(resources.getColor(R.color.text_primary)))
            setCloseIconVisible(false)
            setTextAppearance(R.style.ChipTextStyle)
        }
        return ViewHolder(chip)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(query: String) {
            (itemView as com.google.android.material.chip.Chip).text = query
            itemView.setOnClickListener { onClick(query) }
        }
    }
}

class TrendingAdapter(
    private val onClick: (Song) -> Unit
) : RecyclerView.Adapter<TrendingAdapter.ViewHolder>() {

    private var items: List<Song> = emptyList()

    fun submitList(newItems: List<Song>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trending_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivCover: ImageView = view.findViewById(R.id.ivTrendingCover)
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvArtist: TextView = view.findViewById(R.id.tvArtist)
        private val tvPlayCount: TextView = view.findViewById(R.id.tvPlayCount)

        fun bind(song: Song) {
            tvTitle.text = song.title
            tvArtist.text = song.artist
            tvPlayCount.text = "${song.playCount} reproducciones"

            Glide.with(itemView.context)
                .load(song.albumArt ?: R.drawable.ic_music_note)
                .apply(RequestOptions().transform(RoundedCorners(16)).placeholder(R.drawable.ic_music_note).error(R.drawable.ic_music_note))
                .into(ivCover)

            itemView.setOnClickListener { onClick(song) }
        }
    }
}