package com.project24.animexapp

import android.app.Dialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.youtube.player.YouTubeBaseActivity
import com.google.android.youtube.player.YouTubeInitializationResult
import com.google.android.youtube.player.YouTubePlayer
import com.google.android.youtube.player.YouTubePlayerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.project24.animexapp.api.*
import dev.failsafe.RetryPolicy
import dev.failsafe.retrofit.FailsafeCall
import retrofit2.Response
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import kotlin.collections.ArrayList


class AnimeDetails : YouTubeBaseActivity() {
    private var animeID : Long = -1
    private var animeTitleKitsu : String = ""
    private var YOUTUBE_API_KEY: String? = ""
    private lateinit var firebaseAuth: FirebaseAuth
    private var youTubePlayerView : YouTubePlayerView? = null
    private lateinit var reviewsList: List<Reviews>
    private lateinit var reviewsAnimeRV: RecyclerView
    private lateinit var reviewsAnimeAdapter: ReviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras
        if(extras!=null) {
            // Reset values
            animeID = -1
            animeTitleKitsu = ""

            // Extract values from extras
            animeID = extras.getLong(getString(R.string.anime_id_key), -1)
            animeTitleKitsu = extras.getString(getString(R.string.anime_id_kitsu), "No Title")
        }

        setContentView(R.layout.activity_anime_details)

        firebaseAuth = FirebaseAuth.getInstance()
        val currentUserEmail = firebaseAuth.currentUser?.email
        val nav_account = findViewById<TextView>(R.id.nav_account)


        if(currentUserEmail == null) {
            nav_account.text = " "
        } else {
            nav_account.text = "Welcome, $currentUserEmail"
        }

        YOUTUBE_API_KEY = this.packageManager.getApplicationInfo(
            this.packageName,
            PackageManager.GET_META_DATA
        ).metaData.getString("com.project24.animexapp.YoutubeKey")

        grabAnimeInfo()
    }

    private fun grabAnimeInfo() {
        if (animeID.toInt() == -1 && animeTitleKitsu == "No Title"){
            return //Indicates the previous activity did not correctly pass the animeID
        }

        // If Jikan was used to provide the animeID to AnimeDetails
        else if (animeID.toInt() != -1) {
            val client = JikanApiClient.apiService.getAnimeByID(animeID)

            val retryPolicy = RetryPolicy.builder<Response<AnimeSearchByIDResponse>>()
                .withDelay(Duration.ofSeconds(1))
                .withMaxRetries(3)
                .build()

            val failsafeCall = FailsafeCall.with(retryPolicy).compose(client)

            val cFuture = failsafeCall.executeAsync()
            cFuture.thenApply {
                if (it.isSuccessful) {
                    if (it.body() != null) {
                        val animeData = it.body()!!.animeData

                        setAnimeDetails(animeData)
                        SetUpStarsRating(animeData)
                        setButtons(animeData)
                        setReviewDialog(animeData)
                        setReviewAdapter(animeData)
                    }
                }
            }

            val client2 = JikanApiClient.apiService.getAnimeCharacterById(animeID)

            val retryPolicy2 = RetryPolicy.builder<Response<AnimeCharacterSearchResponse>>()
                .withDelay(Duration.ofSeconds(1))
                .withMaxRetries(3)
                .build()

            val failsafeCall2 = FailsafeCall.with(retryPolicy2).compose(client2)

            val cFuture2 = failsafeCall2.executeAsync()
            cFuture2.thenApply {
                if (it.isSuccessful) {
                    if (it.body() != null) {
                        setAnimeCharacterDetails(it.body()!!.animeData)
                    }
                }
            }
        }

        // If Kitsu was used to provide animeID to AnimeDetails
        else{
            val animeTitleQuery = convertToQuery(animeTitleKitsu)
            val client = JikanApiClient.apiService.requestAnime(query = animeTitleQuery)

            val retryPolicy = RetryPolicy.builder<Response<AnimeSearchResponse>>()
                .withDelay(Duration.ofSeconds(1))
                .withMaxRetries(3)
                .build()

            val failsafeCall = FailsafeCall.with(retryPolicy).compose(client)

            // Grab Anime details
            val cFuture = failsafeCall.executeAsync()
            cFuture.thenApply {
                if (it.isSuccessful) {
                    if (it.body() != null) {
                        val animeData = it.body()!!.result.get(0)
                        animeID = animeData.mal_id

                        // Set Anime Details
                        setAnimeDetails(animeData)
                        SetUpStarsRating(animeData)
                        setButtons(animeData)
                        setReviewDialog(animeData)
                        setReviewAdapter(animeData)

                        // Set Character Details
                        val newAnimeID = animeData.mal_id
                        val client2 = JikanApiClient.apiService.getAnimeCharacterById(newAnimeID)
                        val retryPolicy2 = RetryPolicy.builder<Response<AnimeCharacterSearchResponse>>()
                            .withDelay(Duration.ofSeconds(1))
                            .withMaxRetries(3)
                            .build()
                        val failsafeCall2 = FailsafeCall.with(retryPolicy2).compose(client2)
                        val cFuture2 = failsafeCall2.executeAsync()
                        cFuture2.thenApply {
                            if (it.isSuccessful) {
                                if (it.body() != null) {
                                    setAnimeCharacterDetails(it.body()!!.animeData)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun convertToQuery(givenTitle: String): String {
        var resultQuery = ""
        val stringArr = givenTitle.split(" ").toTypedArray()
        for (str in stringArr){
            if (str == stringArr.get(stringArr.size - 1)){
                resultQuery += str
            }
            else{
                resultQuery += str
                resultQuery += ","
            }
        }
        return resultQuery
    }

    private fun setReviewAdapter(animeData: Anime) {
        firebaseAuth = FirebaseAuth.getInstance()
        val db = Firebase.firestore
        reviewsList = emptyList()
        reviewsAnimeRV = findViewById(R.id.reviewsRecycler)
        reviewsAnimeAdapter = ReviewAdapter(reviewsList)
        val noReviewsYet = findViewById<TextView>(R.id.noReviewsYet)

        reviewsAnimeRV.layoutManager = LinearLayoutManager (
            this,
            LinearLayoutManager.VERTICAL, false
        )

        reviewsAnimeRV.adapter = reviewsAnimeAdapter


        db.collection("Reviews").document(animeData.mal_id.toString()).collection("Reviews").get()
            .addOnSuccessListener { reviews ->
                for (review in reviews) {
                    val reviewTitle: String = review.data.getValue("reviewTitle") as String
                    val reviewComment: String = review.data.getValue("reviewComment") as String
                    val reviewUser: String = review.data.getValue("username") as String
                    val reviewSpoiler: String = review.data.getValue("reviewSpoilers") as String
                    val reviewDate: String = review.data.getValue("reviewDate") as String
                    reviewsList = reviewsList + Reviews(animeData.mal_id, reviewTitle, reviewComment, reviewSpoiler, reviewUser, reviewDate)
                    reviewsAnimeAdapter.reviewList = reviewsList
                    reviewsAnimeAdapter.notifyDataSetChanged()
                    Log.d("MAL_IDFAV", reviewComment)

                }

                if(reviewsList.isEmpty()) {
                    noReviewsYet.visibility = View.VISIBLE
                } else {
                    noReviewsYet.visibility = View.GONE
                }
            }
    }

    private fun SetUpStarsRating(animeData: Anime) {
        val starButtons = ArrayList<ImageButton>()
        starButtons.add(findViewById(R.id.imageButtonAnimeDetailsStar1))
        starButtons.add(findViewById(R.id.imageButtonAnimeDetailsStar2))
        starButtons.add(findViewById(R.id.imageButtonAnimeDetailsStar3))
        starButtons.add(findViewById(R.id.imageButtonAnimeDetailsStar4))
        starButtons.add(findViewById(R.id.imageButtonAnimeDetailsStar5))
        starButtons.add(findViewById(R.id.imageButtonAnimeDetailsStar6))
        starButtons.add(findViewById(R.id.imageButtonAnimeDetailsStar7))
        starButtons.add(findViewById(R.id.imageButtonAnimeDetailsStar8))
        starButtons.add(findViewById(R.id.imageButtonAnimeDetailsStar9))
        starButtons.add(findViewById(R.id.imageButtonAnimeDetailsStar10))

        for (i in 0..9) {
            starButtons[i].setOnClickListener() {
                for (j in 0..i) {
                    starButtons[j].setColorFilter(resources.getColor(R.color.main_color))
                }

                for (k in i + 1..9) {
                    starButtons[k].setColorFilter(resources.getColor(R.color.placehold_gray))
                }
            }
        }
    }

    private fun setReviewDialog(animeData: Anime) {
        val submitAReview = findViewById<Button>(R.id.submitAReview)
        val reviewDialog = Dialog(this)
        firebaseAuth = FirebaseAuth.getInstance()
        val db = Firebase.firestore
        val currentUserID = firebaseAuth.currentUser?.uid
        var currentUsername: String


        reviewDialog.setContentView(R.layout.dialog_review)
        reviewDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val reviewRatingSpinner = reviewDialog.findViewById(R.id.reviewRatingSpinner) as Spinner
        val reviewArrow = reviewDialog.findViewById(R.id.spinnerArrow) as ImageView
        val submitReviewDialog = reviewDialog.findViewById(R.id.reviewSubmitButton) as Button
        val reviewComment = reviewDialog.findViewById(R.id.reviewAnimeComment) as EditText
        val reviewAnimeTitle = reviewDialog.findViewById(R.id.reviewAnimeTitle) as EditText
        val reviewSpoilersCheckbox = reviewDialog.findViewById(R.id.reviewSpoilersCheckbox) as CheckBox

        reviewArrow.setOnClickListener {
            reviewRatingSpinner.performClick()
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.reviewrating,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            reviewRatingSpinner.adapter = adapter
        }

        submitAReview.setOnClickListener {
            if (currentUserID == null) {
                Toast.makeText(this, "You must be logged in to submit a review", Toast.LENGTH_SHORT).show()
            } else {
                reviewDialog.show()
                submitReviewDialog.setOnClickListener {
                    val userReviewComment = reviewComment.text.toString()
                    val userReviewTitle = reviewAnimeTitle.text.toString()
                    val sdf = SimpleDateFormat("MM/dd/yyyy")
                    val currentDate = sdf.format(Date())
                    val spoilers = if(reviewSpoilersCheckbox.isChecked) { "yes" } else { "no" }

                    val docRef = db.collection("Users").document(currentUserID)
                    docRef.get()
                        .addOnSuccessListener { document ->
                            if (document != null) {
                                currentUsername = document.data?.get("username").toString()
                                db.collection("Reviews").document(animeData.mal_id.toString()).collection("Reviews").add(Reviews(animeData.mal_id, userReviewTitle, userReviewComment, spoilers, currentUsername, currentDate))
                            } else {
                                Log.d(TAG, "No such document")
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.d(TAG, "get failed with ", exception)
                        }
                    reviewDialog.dismiss()
                    this.recreate()
                }
            }
        }
    }

    private fun setButtons(animeData: Anime) {
        firebaseAuth = FirebaseAuth.getInstance()
        val db = Firebase.firestore
        val currentUserID = firebaseAuth.currentUser?.uid

        var favourite = 0; var watchlater = 0; var watching = 0;
        val favouriteButton = findViewById<ImageButton>(R.id.imageButtonAnimeDetailsFavourite)
        val watchLaterButton = findViewById<ImageButton>(R.id.imageButtonAnimeDetailsWatchLater)
        val watcthingButton = findViewById<ImageButton>(R.id.imageButtonAnimeDetailsWatching)

        val favDocRef = db.collection("Users").document(currentUserID.toString()).collection("Favourites").document(animeID.toString())
        val watchLaterDocRef = db.collection("Users").document(currentUserID.toString()).collection("WatchLater").document(animeID.toString())
        val watchingDocRef = db.collection("Users").document(currentUserID.toString()).collection("Watching").document(animeID.toString())


        // keep buttons selected if clicked, else gray
        favDocRef.get().addOnSuccessListener {
            if(it.exists()) {
                favouriteButton.setColorFilter(resources.getColor(R.color.main_color))
                favouriteButton.setOnClickListener() {
                    favouriteButton.setColorFilter(resources.getColor(R.color.placehold_gray))
                    favDocRef.delete()
                }
            }
        }

        watchLaterDocRef.get().addOnSuccessListener {
            if(it.exists()) {
                watchLaterButton.setColorFilter(resources.getColor(R.color.main_color))
                watchLaterButton.setOnClickListener() {
                    watchLaterButton.setColorFilter(resources.getColor(R.color.placehold_gray))
                    watchLaterDocRef.delete()
                }
            }
        }

        watchingDocRef.get().addOnSuccessListener {
            if(it.exists()) {
                watcthingButton.setColorFilter(resources.getColor(R.color.main_color))
                watcthingButton.setOnClickListener() {
                    watcthingButton.setColorFilter(resources.getColor(R.color.placehold_gray))
                    watchingDocRef.delete()
                }
            }
        }

        favouriteButton.setOnClickListener() {
            when(favourite++ % 2 ) {
                0 ->
                {
                    favouriteButton.setColorFilter(resources.getColor(R.color.main_color))

                    if(db.collection("Users").document(currentUserID.toString()).collection("Favourites").document(animeID.toString()).equals(animeID.toString()))
                    else if(db.collection("AnimeData").document(animeData.mal_id.toString()).equals(animeData.mal_id.toString()))
                    else {
                        db.collection("Users").document(currentUserID.toString()).collection("Favourites").document(animeID.toString()).set(Favourite(animeData.mal_id, animeData.imageData!!.jpg!!.URL, animeData.title, animeData.englishTitle, animeData.japaneseTitle))
                        db.collection("AnimeData").document(animeData.mal_id.toString()).set(LocalAnime(animeData.mal_id, animeData.title, animeData.imageData!!.jpg!!.URL, animeData.synopsis, animeData.score, animeData.trailerData))
                    }
                }

                1 ->
                {
                    favouriteButton.setColorFilter(resources.getColor(R.color.placehold_gray))
                    favDocRef.delete()
                }
            }
        }

        watchLaterButton.setOnClickListener() {
            when(watchlater++ % 2 ) {
                0 ->
                {
                    watchLaterButton.setColorFilter(resources.getColor(R.color.main_color))
                    if(db.collection("Users").document(currentUserID.toString()).collection("WatchLater").document(animeID.toString()).equals(animeID.toString()))
                         if(db.collection("AnimeData").document(animeData.mal_id.toString()).equals(animeData.mal_id.toString()))
                    else {
                        db.collection("Users").document(currentUserID.toString()).collection("WatchLater").document(animeID.toString()).set(Favourite(animeData.mal_id, animeData.imageData!!.jpg!!.URL, animeData.title, animeData.englishTitle, animeData.japaneseTitle))
                        db.collection("AnimeData").document(animeData.mal_id.toString()).set(LocalAnime(animeData.mal_id, animeData.title, animeData.imageData!!.jpg!!.URL, animeData.synopsis, animeData.score, animeData.trailerData))
                    }
                }
                1 ->
                {
                    watchLaterButton.setColorFilter(resources.getColor(R.color.placehold_gray))
                    watchLaterDocRef.delete()
                }
            }
        }

        watcthingButton.setOnClickListener() {
            when(watching++ % 2 ) {
                0 ->
                {
                    watcthingButton.setColorFilter(resources.getColor(R.color.main_color))
                    if(db.collection("Users").document(currentUserID.toString()).collection("Watching").document(animeID.toString()).equals(animeID.toString()))
                    else if(db.collection("AnimeData").document(animeData.mal_id.toString()).equals(animeData.mal_id.toString()))
                    else {
                        db.collection("Users").document(currentUserID.toString()).collection("Watching").document(animeID.toString()).set(Favourite(animeData.mal_id, animeData.imageData!!.jpg!!.URL, animeData.title, animeData.englishTitle, animeData.japaneseTitle))
                        db.collection("AnimeData").document(animeData.mal_id.toString()).set(LocalAnime(animeData.mal_id, animeData.title, animeData.imageData!!.jpg!!.URL, animeData.synopsis, animeData.score, animeData.trailerData))
                    }
                }


                1 ->
                {
                    watcthingButton.setColorFilter(resources.getColor(R.color.placehold_gray))
                    watchingDocRef.delete()
                }
            }
        }
    }

    private fun setAnimeCharacterDetails(characterList: List<Character>) {
        val characterRV = findViewById<RecyclerView>(R.id.recyclerViewAnimeDetailsCharacters)

        val characterAdapter = CharacterRVAdapter(characterList)

        characterRV.layoutManager = LinearLayoutManager(this,
            LinearLayoutManager.HORIZONTAL,false)
        characterRV.adapter = characterAdapter
    }

    private fun setAnimeDetails(animeData: Anime) {
        setAnimeTitle(animeData)
        setAnimeTrailer(animeData.trailerData?.youtubeID, animeData.imageData?.jpg)
        setAnimeSynopsis(animeData.synopsis)
        setAnimeScore(animeData.score.toString())
    }

    private fun setAnimeTitle(animeData: Anime) {
        val txt = findViewById<TextView>(R.id.textViewAnimeDetailsTitle)

        val chosenLanguagePreferences = getSharedPreferences(getString(
            R.string.shared_preference_language_key), Context.MODE_PRIVATE)
        val chosenLanguage =
            chosenLanguagePreferences.getString(getString(R.string.chosen_language_key), getString(R.string.english))!!

        if (chosenLanguage == getString(R.string.english)
            && animeData.englishTitle != null
        ) {
            txt.text = animeData.englishTitle
        }
        else{
            txt.text = animeData.title
        }
    }

    private fun setAnimeScore(givenScore: String) {
        val score = findViewById<TextView>(R.id.textViewAnimeDetailsScore)
        score.text = givenScore
    }

    private fun setAnimeTrailer(youtubeID: String?, givenJPG: Jpg?) {

        val youTubePlayerView : YouTubePlayerView = findViewById(R.id.youtubePlayerView)
        val animeDetailsImageView = findViewById<ImageView>(R.id.animeDetailsImageView)

        if (youtubeID == null){
            youTubePlayerView.visibility = View.GONE
            Glide.with(applicationContext)
                .load(givenJPG!!.URL)
                .fitCenter()
                .into(animeDetailsImageView)
            return
        }
        else{
            animeDetailsImageView.visibility = View.GONE
        }

        youTubePlayerView.initialize(YOUTUBE_API_KEY, object:YouTubePlayer.OnInitializedListener {
            override fun onInitializationSuccess(
                provider: YouTubePlayer.Provider?,
                player: YouTubePlayer?,
                bln: Boolean
            ) {
                player?.cueVideo(youtubeID)
                player?.play()
            }

            override fun onInitializationFailure(
                provider: YouTubePlayer.Provider?,
                result: YouTubeInitializationResult?
            ) {
                Toast.makeText(applicationContext, "Something went wrong", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setAnimeSynopsis(synopsis: String?) {
        if (synopsis == null){
            return
        }

        val syn = findViewById<TextView>(R.id.textViewAnimeDetailsSynopsis)
        syn.text = synopsis.dropLast(25)
    }
}