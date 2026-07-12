package com.dezzmusic.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.dezzmusic.R
import com.dezzmusic.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSettings()
    }

    private fun setupSettings() {
        binding.settingsAppearance.setOnClickListener {
            launchSettingsDetail("appearance")
        }

        binding.settingsPlayback.setOnClickListener {
            launchSettingsDetail("playback")
        }

        binding.settingsStorage.setOnClickListener {
            launchSettingsDetail("storage")
        }

        binding.settingsNotifications.setOnClickListener {
            launchSettingsDetail("notifications")
        }

        binding.settingsAccount.setOnClickListener {
            launchSettingsDetail("account")
        }

        binding.settingsAbout.setOnClickListener {
            launchSettingsDetail("about")
        }

        binding.btnDeezload.setOnClickListener {
            val intent = Intent(requireContext(), DeezloadBotActivity::class.java)
            startActivity(intent)
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun launchSettingsDetail(type: String) {
        val intent = Intent(requireContext(), SettingsDetailActivity::class.java).apply {
            putExtra("settings_type", type)
        }
        startActivity(intent)
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
