package com.dezzmusic.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.dezzmusic.R
import com.dezzmusic.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNav()
        if (savedInstanceState == null) {
            loadFragment(ChatsFragment(), false)
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chats -> {
                    loadFragment(ChatsFragment(), true)
                    true
                }
                R.id.nav_library -> {
                    loadFragment(LibraryFragment(), true)
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment(), true)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment, withAnimation: Boolean) {
        if (fragment.javaClass == currentFragment?.javaClass) return

        val transaction = supportFragmentManager.beginTransaction()

        if (withAnimation) {
            transaction.setCustomAnimations(
                R.anim.fade_in,
                R.anim.fade_out,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        }

        transaction.replace(R.id.fragmentContainer, fragment)
        transaction.commit()

        currentFragment = fragment
    }
}
