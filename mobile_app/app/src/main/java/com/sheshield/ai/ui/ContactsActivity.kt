package com.sheshield.ai.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sheshield.ai.R
import com.sheshield.ai.data.EmergencyContact
import com.sheshield.ai.data.local.AppDatabase
import com.sheshield.ai.databinding.ActivityContactsBinding
import kotlinx.coroutines.launch

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private lateinit var adapter: ContactsAdapter
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadContacts()

        binding.addContactButton.setOnClickListener {
            showAddContactDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = ContactsAdapter(emptyList()) { contact ->
            deleteContact(contact)
        }
        binding.contactsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.contactsRecyclerView.adapter = adapter
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            val contacts = database.contactDao().getAll()
            adapter.updateContacts(contacts)
        }
    }

    private fun showAddContactDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.nameInput)
        val phoneInput = dialogView.findViewById<EditText>(R.id.phoneInput)

        AlertDialog.Builder(this, R.style.Theme_SheShieldAI_Dialog)
            .setTitle("Add Emergency Contact")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString()
                val phone = phoneInput.text.toString()
                if (name.isNotBlank() && phone.isNotBlank()) {
                    saveContact(name, phone)
                } else {
                    Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveContact(name: String, phone: String) {
        lifecycleScope.launch {
            val contact = EmergencyContact(name = name, phoneNumber = phone, isPriority = true)
            database.contactDao().insert(contact)
            loadContacts()
        }
    }

    private fun deleteContact(contact: EmergencyContact) {
        AlertDialog.Builder(this, R.style.Theme_SheShieldAI_Dialog)
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete ${contact.name}?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    database.contactDao().delete(contact)
                    loadContacts()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
