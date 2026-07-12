package com.dezzmusic.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
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
            val intent = android.content.Intent(requireContext(), SettingsDetailActivity::class.java)
            intent.putExtra("settings_type", "appearance")
            startActivity(intent)
        }

        binding.settingsPlayback.setOnClickListener {
            val intent = android.content.Intent(requireContext(), SettingsDetailActivity::class.java)
            intent.putExtra("settings_type", "playback")
            startActivity(intent)
        }

        binding.settingsStorage.setOnClickListener {
            val intent = android.content.Intent(requireContext(), SettingsDetailActivity::class.java)
            intent.putExtra("settings_type", "storage")
            startActivity(intent)
        }

        binding.settingsNotifications.setOnClickListener {
            val intent = android.content.Intent(requireContext(), SettingsDetailActivity::class.java)
            intent.putExtra("settings_type", "notifications")
            startActivity(intent)
        }

        binding.settingsAccount.setOnClickListener {
            val intent = android.content.Intent(requireContext(), SettingsDetailActivity::class.java)
            intent.putExtra("settings_type", "account")
            startActivity(intent)
        }

        binding.settingsAbout.setOnClickListener {
            val intent = android.content.Intent(requireContext(), SettingsDetailActivity::class.java)
            intent.putExtra("settings_type", "about")
            startActivity(intent)
        }

        binding.btnDeezload.setOnClickListener {
            val intent = android.content.Intent(requireContext(), DeezloadBotActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
