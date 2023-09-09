package org.akanework.gramophone.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R

class ArtistDecorAdapter(
    private val context: Context,
    private var artistCount: Int,
    private val artistAdapter: ArtistAdapter,
) : RecyclerView.Adapter<ArtistDecorAdapter.ViewHolder>() {
    private var sortStatus = 0

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.general_decor, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val songText =
            artistCount.toString() + ' ' +
                if (artistCount <= 1) context.getString(R.string.artist) else context.getString(R.string.artists)
        holder.songCounter.text = songText
        holder.sortButton.setOnClickListener {
            val popupMenu = PopupMenu(context, it)
            popupMenu.inflate(R.menu.sort_menu_artist)

            when (sortStatus) {
                0 -> {
                    popupMenu.menu.findItem(R.id.name).isChecked = true
                }

                1 -> {
                    popupMenu.menu.findItem(R.id.size).isChecked = true
                }
            }

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.name -> {
                        if (!menuItem.isChecked) {
                            artistAdapter.sortBy { it2 -> it2.title }
                            menuItem.isChecked = true
                            sortStatus = 0
                        }
                    }

                    R.id.size -> {
                        if (!menuItem.isChecked) {
                            artistAdapter.sortByDescendingInt { it2 -> it2.songList.size }
                            menuItem.isChecked = true
                            sortStatus = 1
                        }
                    }
                }
                true
            }
            popupMenu.show()
        }
    }

    override fun getItemCount(): Int = 1

    inner class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val sortButton: MaterialButton = view.findViewById(R.id.sort)
        val songCounter: TextView = view.findViewById(R.id.song_counter)
    }

    fun updateSongCounter(count: Int) {
        sortStatus = 0
        artistCount = count
        notifyItemChanged(0)
    }

    fun isCounterEmpty(): Boolean = artistCount == 0
}
