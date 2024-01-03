package com.animeiat


import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.nicehttp.Requests
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities


class Animeiat : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://api.rstream.cc"
    val pageUrl = "https://streamsaga.online"
    private val vidSrcAPI = "https://vidsrc.in"
    override var name = "Animeiat"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    fun deobfstr(hash: String, index: String): String {
        var result = ""
        for (i in hash.indices step 2) {
            val j = hash.substring(i, i + 2)
            result += (j.toInt(16) xor index[(i / 2) % index.length].code).toChar()
        }
        return result
    }


    suspend fun invokeVidSrc(id: Int? = null, season: Int? = null, episode: Int? = null, callback: (ExtractorLink) -> Unit) {
        val url = if (season == null) {
            "$vidSrcAPI/embed/movie?tmdb=$id"
        } else {
            "$vidSrcAPI/embed/tv?tmdb=$id&season=$season&episode=$episode"
        }

        val iframedoc = app.get(url).document.select("iframe#player_iframe").attr("src").let { httpsify(it) }
        val doc = app.get(iframedoc, referer = url).document

        val index = doc.select("body").attr("data-i")
        val hash = doc.select("div#hidden").attr("data-h")
        val srcrcp = deobfstr(hash, index)

        Log.d("srcrcp", "srcrcp: $srcrcp")

        val script = app.get(httpsify(srcrcp), referer = iframedoc).document.selectFirst("script:containsData(Playerjs)")?.data()
        Log.d("scriptsrcrcp", "script: $script")
        val video = script?.substringAfter("file:\"#2")?.substringBefore("\"")?.replace(Regex("(//\\S+?=)"), "")?.let { base64Decode(it) }
        Log.d("video", "video: $video")

        callback.invoke(ExtractorLink("Vidsrc", "Vidsrc", video
                ?: return, "https://vidsrc.stream/", Qualities.P1080.value, INFER_TYPE))
    }


    data class Load(
            val success: Boolean,
            val data: Item
    )


    data class Episodes(
            val success: Boolean,
            val data: List<EpisodeData>
    )

    data class EpisodeData(
            val number: Int,
            val title: String,
            val description: String,
            val image: String,
            val runtime: Int
    )


    data class MediaItem(
            val id: Int,
            val title: String,
            val poster: String,
            val type: String
    )

    data class SearchResult(
            val success: Boolean,
            val data: List<MediaItem>
    )

    data class ApiCollection(
            val title: String,
            val items: List<MediaItem>
    )


    data class All(
            val success: Boolean,
            val hero: Hero?,
            val data: Data
    )

    data class Data(
            val collections: List<ApiCollection>
    )

    data class Genre(
            val id: Int,
            val name: String
    )

    data class Item(
            val id: Int?,
            val title: String,
            val description: String?,
            val date: String?,
            val seasons: Int?,
            val genres: List<Genre>?,
            val type: String?,
            val posterPath: String?,
            val hero: Hero?,
//        val collections: List<Collection>?,
            val images: Images?,
            val runtime: Int?,
            val poster: String?,
            //   val suggested: List<SuggestedItem>

    )
//     data class SuggestedItem(
//            val id: Int,
//            val title: String,
//            val poster: String,
//            val type: String
//    )

    data class Hero(
            val id: Int,
            val type: String,
            val title: String,
            val description: String,
            val images: Images?
    )

    data class Images(
            val logo: String,
            val backdrop: String,
            val poster: String?
    )

    override val mainPage = mainPageOf(
            "$mainUrl/browse?title=1" to "Trending movies this week",
            "$mainUrl/browse?title=2" to "Trending series this week",
            "$mainUrl/browse?title=3" to "Top rated movies",
            "$mainUrl/browse?title=4" to "Top rated series",
            "$mainUrl/browse?title=5" to "On the air series",

            )


    override suspend fun getMainPage(
            page: Int,
            request: MainPageRequest
    ): HomePageResponse {
        val json = parseJson<All>(app.get(request.data).text)
        Log.d("DebugListjson", "json: ${json.data}")

        val title = when {
            request.data.contains("?title=1") -> json.data.collections.find { it.title.contains("Trending movies this week") }?.title ?: "Invalid title"
            request.data.contains("?title=2") -> json.data.collections.find { it.title.contains("Trending series this week") }?.title ?: "Invalid title"
            request.data.contains("?title=3") -> json.data.collections.find { it.title.contains("Top rated movies") }?.title ?: "Invalid title"
            request.data.contains("?title=4") -> json.data.collections.find { it.title.contains("Top rated series") }?.title ?: "Invalid title"
            request.data.contains("?title=5") -> json.data.collections.find { it.title.contains("On the air series") }?.title ?: "Invalid title"
            else -> "Invalid title"
        }

        val list = json.data.collections.find { it.title == title }
                ?.items

                ?.map {
                    Log.d("DebugListjson", "title: ${it.title}, type: ${it.poster}, id: ${it.id}")
                    val type = if (it.type == "movie") TvType.Movie else TvType.TvSeries

                    MovieSearchResponse(
                            it.title,
                            if (it.type == "movie") "https://api.rstream.cc/movie/${it.id}" else "https://api.rstream.cc/series/${it.id}",
                            this@Animeiat.name,
                            type,
                            it.poster,
                            null,
                            null
                    )
                } ?: emptyList()

        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val json = parseJson<SearchResult>(app.get("$mainUrl/search?q=$query").text)
        Log.d("DebugListjsonsearch", "json: ${json.data}")
        return json.data.map {
            val type = if (it.type == "movie") TvType.Movie else TvType.TvSeries
            MovieSearchResponse(
                    it.title,
                    url = if (it.type == "movie") "$mainUrl/movie/${it.id}" else "$mainUrl/series/${it.id}",
                    this@Animeiat.name,
                    type,
                    it.poster,
                    null,
                    null
            )
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val loadSession = Requests()
        val request = loadSession.get(url).text
        val json = parseJson<Load>(request)
        val isMovie = url.contains("movie")
        if (isMovie) {
            val moviid = json.data?.id
            val title = json.data?.title ?: ""
            val description = json.data?.description ?: ""
            val year = json.data?.date?.split("-")?.get(0)?.toIntOrNull()
            val posterUrl = json.data?.images?.poster ?: ""
            val duration = json.data?.runtime ?: 0
            val tags = json.data?.genres?.map { it.name } ?: emptyList()


            return newMovieLoadResponse(
                    title,
                    "$mainUrl/embed/movie/$moviid?v=3.2.0&n=StreamSaga&o=https://streamsaga.online",
                    TvType.Movie,
                    url
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = description
                this.duration = duration
                this.tags = tags

            }
        } else {
            val seasonsCount = json.data?.seasons
            val seasonsId = json.data?.id
            val episodes = arrayListOf<Episode>()

            (1..seasonsCount!!).map { seasonNumber ->
                val episodesRequest =
                        loadSession.get("$mainUrl/episodes/$seasonsId?s=$seasonNumber").text
                val episodesJson = parseJson<Episodes>(episodesRequest)
                episodesJson.data.map {
                    episodes.add(
                            Episode(
                                    "$mainUrl/embed/series/$seasonsId?v=3.2.0&n=StreamSaga&o=https://streamsaga.online&s=$seasonNumber&e=" + it.number,
                                    it.title,
                                    seasonNumber,
                                    it.number,
                            )
                    )
                }
            }

            return newAnimeLoadResponse(
                    json.data?.title.toString(),
                    url,
                    if (json.data?.type == "movie") TvType.AnimeMovie else if (json.data?.type == "tv") TvType.Anime else TvType.OVA
            ) {
                japName = json.data?.title
                engName = json.data?.title
                posterUrl = json.data?.images?.poster ?: ""
                this.year = json.data?.date?.split("-")?.get(0)?.toIntOrNull()
                addEpisodes(DubStatus.Subbed, episodes)
                plot = json.data?.description
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {


        val movieIdRegex = Regex("/series/(\\d+)")
        val movieIdRegexkek = Regex("/movie/(\\d+)")
        val movieId = movieIdRegex.find(data)?.groupValues?.get(1)?.toIntOrNull()

        val seasonRegex = Regex("[?&]s=(\\d+)")
        val season = seasonRegex.find(data)?.groupValues?.get(1)?.toIntOrNull()

        val episodeRegex = Regex("[?&]e=(\\d+)")
        val episode = episodeRegex.find(data)?.groupValues?.get(1)?.toIntOrNull()

        if (data.contains("movie")) {
            val movieId = movieIdRegexkek.find(data)?.groupValues?.get(1)?.toIntOrNull()
            Log.d("DebugListjson", "movieId: $movieId")
            invokeVidSrc(movieId, null, null, callback)
            return true
        }

        invokeVidSrc(movieId, season, episode, callback)
        return true
    }

}
