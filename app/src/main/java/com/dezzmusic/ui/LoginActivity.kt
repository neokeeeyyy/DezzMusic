package com.dezzmusic.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dezzmusic.databinding.ActivityLoginBinding
import com.dezzmusic.telegram.TelegramManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var currentStep = 0
    private var phoneNumber = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if already logged in
        if (TelegramManager.getInstance(this).isLoggedIn()) {
            navigateToMain()
            return
        }

        setupUI()
    }

    private fun setupUI() {
        binding.btnContinue.setOnClickListener {
            when (currentStep) {
                0 -> {
                    phoneNumber = binding.etPhone.text.toString().trim()
                    if (phoneNumber.isNotEmpty()) {
                        requestCode()
                    } else {
                        Toast.makeText(this, "Ingresa tu número", Toast.LENGTH_SHORT).show()
                    }
                }
                1 -> {
                    val code = binding.etCode.text.toString().trim()
                    if (code.isNotEmpty()) {
                        verifyCode(code)
                    } else {
                        Toast.makeText(this, "Ingresa el código", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun requestCode() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnContinue.isEnabled = false

        lifecycleScope.launch {
            val result = TelegramManager.getInstance(this@LoginActivity).login(phoneNumber)
            when (result) {
                is com.dezzmusic.telegram.LoginResult.CodeSent -> {
                    currentStep = 1
                    binding.etPhone.visibility = android.view.View.GONE
                    binding.etCode.visibility = android.view.View.VISIBLE
                    binding.tvSubtitle.text = "Código enviado a $phoneNumber"
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnContinue.isEnabled = true
                }
                is com.dezzmusic.telegram.LoginResult.Error -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnContinue.isEnabled = true
                    Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun verifyCode(code: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnContinue.isEnabled = false

        lifecycleScope.launch {
            val success = TelegramManager.getInstance(this@LoginActivity).verifyCode(code)
            if (success) {
                navigateToMain()
            } else {
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnContinue.isEnabled = true
                Toast.makeText(this@LoginActivity, "Código inválido", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
