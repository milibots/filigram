package com.filigram.cinema

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Playlist(
    val id: String,
    val title: String,
    val englishTitle: String,
    val description: String,
    val cover: String,
    val items: MutableList<MovieItem>
)

object PlaylistsManager {

    private val userCustomPlaylists = mutableListOf<Playlist>()
    private var isLoaded = false

    val curatedPlaylists: List<Playlist>
        get() = userCustomPlaylists

    @Synchronized
    fun getAllPlaylists(context: Context): List<Playlist> {
        loadCustomPlaylists(context)
        return userCustomPlaylists
    }

    @Synchronized
    fun addItemToPlaylist(context: Context, playlistId: String, item: MovieItem): Boolean {
        loadCustomPlaylists(context)
        val target = userCustomPlaylists.find { it.id == playlistId } ?: return false
        if (target.items.any { it.id == item.id }) {
            return false
        }
        target.items.add(0, item)
        saveCustomPlaylists(context)
        return true
    }

    @Synchronized
    fun removeItemFromPlaylist(context: Context, playlistId: String, itemId: Int): Boolean {
        loadCustomPlaylists(context)
        val target = userCustomPlaylists.find { it.id == playlistId } ?: return false
        val removed = target.items.removeAll { it.id == itemId }
        if (removed) {
            saveCustomPlaylists(context)
        }
        return removed
    }

    @Synchronized
    fun deletePlaylist(context: Context, playlistId: String): Boolean {
        loadCustomPlaylists(context)
        val removed = userCustomPlaylists.removeAll { it.id == playlistId }
        if (removed) {
            saveCustomPlaylists(context)
        }
        return removed
    }

    @Synchronized
    fun clearAllPlaylists(context: Context): Boolean {
        loadCustomPlaylists(context)
        userCustomPlaylists.clear()
        saveCustomPlaylists(context)
        return true
    }

    @Synchronized
    fun createPlaylist(context: Context, title: String, description: String): Playlist {
        loadCustomPlaylists(context)
        val newPl = Playlist(
            id = "custom_" + System.currentTimeMillis(),
            title = title,
            englishTitle = "پلی‌لیست شخصی",
            description = description,
            cover = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&q=80&w=600",
            items = mutableListOf()
        )
        userCustomPlaylists.add(0, newPl)
        saveCustomPlaylists(context)
        return newPl
    }

    private fun loadCustomPlaylists(context: Context) {
        if (isLoaded) return
        val file = File(context.filesDir, "filigram_custom_playlists.json")
        if (!file.exists()) {
            isLoaded = true
            return
        }
        try {
            val content = file.readText()
            val arr = JSONArray(content)
            userCustomPlaylists.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val itemsArr = obj.optJSONArray("items") ?: JSONArray()
                val list = mutableListOf<MovieItem>()
                for (j in 0 until itemsArr.length()) {
                    val mObj = itemsArr.getJSONObject(j)
                    list.add(
                        MovieItem(
                            id = mObj.getInt("id"),
                            title = mObj.getString("title"),
                            image = mObj.getString("image"),
                            type = mObj.optInt("type", 0),
                            year = mObj.optString("year", "")
                        )
                    )
                }
                userCustomPlaylists.add(
                    Playlist(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        englishTitle = obj.optString("englishTitle", "Custom"),
                        description = obj.optString("description", ""),
                        cover = obj.optString("cover", "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&q=80&w=600"),
                        items = list
                    )
                )
            }
            isLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
            isLoaded = true
        }
    }

    private fun saveCustomPlaylists(context: Context) {
        try {
            val file = File(context.filesDir, "filigram_custom_playlists.json")
            val arr = JSONArray()
            for (pl in userCustomPlaylists) {
                val obj = JSONObject()
                obj.put("id", pl.id)
                obj.put("title", pl.title)
                obj.put("englishTitle", pl.englishTitle)
                obj.put("description", pl.description)
                obj.put("cover", pl.cover)
                val itemsArr = JSONArray()
                for (it in pl.items) {
                    val mObj = JSONObject()
                    mObj.put("id", it.id)
                    mObj.put("title", it.title)
                    mObj.put("image", it.image)
                    mObj.put("type", it.type)
                    mObj.put("year", it.year)
                    itemsArr.put(mObj)
                }
                obj.put("items", itemsArr)
                arr.put(obj)
            }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
