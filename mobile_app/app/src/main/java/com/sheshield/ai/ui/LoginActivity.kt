package com.sheshield.ai.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sheshield.ai.data.remote.ApiService
import com.sheshield.ai.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val apiService by lazy { ApiService.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val prefs = getSharedPreferences("SheShieldPrefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_logged_in", false)) {
            ApiService.authToken = prefs.getString("access_token", null)
            navigateToMain()
            return
        }

        binding.loginButton.setOnClickListener {
            loginUser()
        }

        binding.registerText.text = "Don't have an account? Register here"
        binding.registerText.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser() {
        val email = binding.emailField.text.toString().trim()
        val password = binding.passwordField.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val request = com.sheshield.ai.data.LoginRequest(email, password)
                val response = apiService.login(request)
                if (response.isSuccessful) {
                    val authResponse = response.body()
                    val userId = authResponse?.userId ?: ""
                    val userName = authResponse?.userName ?: ""
                    val token = authResponse?.access_token ?: ""
                    val refreshToken = authResponse?.refresh_token ?: ""
                    
                    ApiService.authToken = token
                    
                    val prefs = getSharedPreferences("SheShieldPrefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("user_id", userId)
                        putString("user_name", userName)
                        putString("access_token", token)
                        putString("refresh_token", refreshToken)
                        putBoolean("is_logged_in", true)
                        apply()
                    }
                    navigateToMain()
                } else {
                    val errorBody = response.errorBody()?.string()
                    val message = try {
                        val json = JSONObject(errorBody ?: "")
                        json.optString("message", "Invalid credentials")
                    } catch (e: Exception) {
                        "Invalid email or password"
                    }
                    Toast.makeText(this@LoginActivity, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Connection Error: Is the server running?", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
