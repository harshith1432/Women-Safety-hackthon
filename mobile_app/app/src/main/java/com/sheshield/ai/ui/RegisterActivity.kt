package com.sheshield.ai.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.sheshield.ai.data.remote.ApiService
import com.sheshield.ai.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val apiService by lazy { ApiService.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.signUpButton.setOnClickListener {
            registerUser()
        }

        binding.loginText.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser() {
        val name = binding.nameField.text.toString().trim()
        val email = binding.emailField.text.toString().trim()
        val phone = binding.phoneField.text.toString().trim()
        val password = binding.passwordField.text.toString().trim()
        val address = binding.addressField.text.toString().trim()
        val guardianName = binding.guardianNameField.text.toString().trim()
        val guardianPhone = binding.guardianPhoneField.text.toString().trim()
        val emergencyContacts = binding.emergencyContactsField.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        val registrationData = com.sheshield.ai.data.RegisterRequest(
            full_name = name,
            phone_number = phone,
            email = email,
            password = password,
            home_address = address,
            guardian_details = "$guardianName ($guardianPhone)",
            emergency_contacts = emergencyContacts
        )

        lifecycleScope.launch {
            try {
                val response = apiService.register(registrationData)
                if (response.isSuccessful) {
                    val body = response.body()
                    val userId = body?.userId ?: "0"
                    val token = body?.access_token ?: ""
                    val refreshToken = body?.refresh_token ?: ""
                    
                    ApiService.authToken = token
                    
                    val prefs = getSharedPreferences("SheShieldPrefs", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("user_id", userId)
                        putString("user_name", name)
                        putString("access_token", token)
                        putString("refresh_token", refreshToken)
                        putBoolean("is_logged_in", true)
                        apply()
                    }

                    // Save contacts locally
                    if (emergencyContacts.isNotBlank()) {
                        val db = com.sheshield.ai.data.local.AppDatabase.getDatabase(this@RegisterActivity)
                        emergencyContacts.split(",").forEach { contactStr ->
                            val parts = contactStr.split(":")
                            val cName = parts[0].trim()
                            val cPhone = if (parts.size > 1) parts[1].trim() else cName
                            if (cName.isNotBlank()) {
                                db.contactDao().insert(com.sheshield.ai.data.EmergencyContact(
                                    name = if (parts.size > 1) cName else "Emergency",
                                    phoneNumber = cPhone,
                                    isPriority = true
                                ))
                            }
                        }
                    }

                    Toast.makeText(this@RegisterActivity, "Registration Successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                    finish()
                } else {
                    val errorBody = response.errorBody()?.string()
                    val message = parseErrorMessage(errorBody)
                    Toast.makeText(this@RegisterActivity, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "Connection Error: Check server status", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun parseErrorMessage(json: String?): String {
        if (json.isNullOrBlank()) return "Registration failed. Please try again."
        return try {
            val map = Gson().fromJson(json, Map::class.java)
            map["message"]?.toString() ?: json.take(100)
        } catch (e: Exception) {
            // If it's HTML or other non-JSON, show a snippet for debugging
            if (json.trim().startsWith("<!doctype", ignoreCase = true) || json.trim().startsWith("<html", ignoreCase = true)) {
                "Server Error (HTML). Please check backend logs."
            } else {
                json.take(100)
            }
        }
    }
}
