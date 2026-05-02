package com.sheshield.ai.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sheshield.ai.R
import com.sheshield.ai.data.EmergencyContact

class ContactsAdapter(
    private var contacts: List<EmergencyContact>,
    private val onDelete: (EmergencyContact) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    class ContactViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(android.R.id.text1)
        val phone: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        holder.name.text = contact.name
        holder.name.setTextColor(0xFFFFFFFF.toInt())
        holder.phone.text = contact.phoneNumber
        holder.phone.setTextColor(0xFF94a3b8.toInt())
        
        holder.itemView.setOnLongClickListener {
            onDelete(contact)
            true
        }
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: List<EmergencyContact>) {
        contacts = newContacts
        notifyDataSetChanged()
    }
}
