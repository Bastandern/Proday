package com.example.proday

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.proday.data.Event
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventsAdapter(
    private val onItemClick: (Event) -> Unit,
    private val onItemLongClick: (Event) -> Unit
) : ListAdapter<Event, EventsAdapter.EventViewHolder>(EventDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view, onItemClick, onItemLongClick)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class EventViewHolder(
        itemView: View,
        private val onItemClick: (Event) -> Unit,
        private val onItemLongClick: (Event) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.eventTitle)
        private val timeTextView: TextView = itemView.findViewById(R.id.eventTime)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.eventDescription)

        fun bind(event: Event) {
            titleTextView.text = event.title
            
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val start = timeFormat.format(Date(event.startTime))
            val end = timeFormat.format(Date(event.endTime))
            timeTextView.text = "$start - $end"

            if (!event.description.isNullOrEmpty()) {
                descriptionTextView.text = event.description
                descriptionTextView.visibility = View.VISIBLE
            } else {
                descriptionTextView.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClick(event) }
            itemView.setOnLongClickListener { 
                onItemLongClick(event)
                true 
            }
        }
    }

    class EventDiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem == newItem
        }
    }
}