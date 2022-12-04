package com.project24.animexapp.ui.home

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project24.animexapp.AnimeDetails
import com.project24.animexapp.ui.LoadingScreens.LoadingBarActivity
import com.project24.animexapp.R
import com.project24.animexapp.api.*

class AnimeCardRVAdapter(var animeList : List<KitsuAnimeData>): RecyclerView.Adapter<AnimeCardRVAdapter.MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.carditem_anime,parent,false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val itemAnime = animeList[position]
        holder.bindAnime(itemAnime)
    }

    override fun getItemCount(): Int {
        return animeList.size
    }

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        private var view: View = itemView
        private lateinit var anime: KitsuAnimeData
        private var imgURL: String? = ""

        init {
            view.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val showAnimeIntent =
                Intent(itemView.context, AnimeDetails::class.java)
            showAnimeIntent.putExtra(
                itemView.context.getString(R.string.anime_id_kitsu),
                this.anime.attributes.title
            )
            itemView.context.startActivity(showAnimeIntent)
            startLoadingActivity(itemView.context)
        }

        private fun startLoadingActivity(context: Context?) {
            val intent = Intent(context, LoadingBarActivity::class.java)
            context?.startActivity(intent)
        }

        fun bindAnime(anime: KitsuAnimeData) {
            this.anime = anime
            this.imgURL = anime.attributes.coverImageData!!.original
            Glide.with(this.itemView).load(imgURL).centerCrop().dontAnimate().into(view.findViewById(R.id.card_img))
            view.findViewById<TextView>(R.id.card_title).text = anime.attributes.title
            view.findViewById<TextView>(R.id.card_synopsis).text = anime.attributes.synopsis
            view.findViewById<TextView>(R.id.card_score).text = anime.attributes.synopsis
            /*
            view.findViewById<TextView>(R.id.card_score).text = (anime.attributes.rating?.toDouble()
                ?.div(10)).toString()


             */
        }


        companion object {
            private val ANIME_ID = "ANIME_ID"
        }
    }




}