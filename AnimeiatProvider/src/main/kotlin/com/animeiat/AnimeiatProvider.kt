package com.animeiat


import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.nicehttp.Requests

class Animeiat : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://api.animeiat.co/v1"
    val pageUrl = "https://www.animeiat.tv"
    override var name = "Animeiat"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes =
        setOf(TvType.Anime, TvType.AnimeMovie)

    data class Data (
        @JsonProperty("anime_name"     ) var animeName     : String? = null,
        @JsonProperty("title"     ) var title     : String? = null,
        @JsonProperty("slug"           ) var slug          : String? = null,
        @JsonProperty("story"          ) var story         : String? = null,
        @JsonProperty("other_names"    ) var otherNames    : String? = null,
        @JsonProperty("total_episodes" ) var totalEpisodes : Int? = null,
        @JsonProperty("number" ) var number : Int? = null,
        @JsonProperty("age"            ) var age           : String? = null,
        @JsonProperty("type"           ) var type          : String? = null,
        @JsonProperty("status"         ) var status        : String? = null,
        @JsonProperty("poster_path"    ) var posterPath    : String? = null,
    )
    data class All (
        @JsonProperty("data"  ) var data  : ArrayList<Data> = arrayListOf(),
    )

    override val mainPage = mainPageOf(
        "$mainUrl/home/sticky-episodes?page=" to "Episodes (H)",
        "$mainUrl/anime?status=completed&page=" to "Completed",
        "$mainUrl/anime?status=ongoing&page=" to "Ongoing",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val json = parseJson<All>(app.get(request.data + page).text)
        val list = json.data.map {
            newAnimeSearchResponse(
                it.animeName ?: it.title.toString(),
                mainUrl + "/anime/" + it.slug.toString().replace("-episode.*".toRegex(),""),
                if (it.type == "movie") TvType.AnimeMovie else if (it.type == "tv") TvType.Anime else TvType.OVA,
            ) {
                addDubStatus(false, it.totalEpisodes ?: it.number)
                this.otherName = it.otherNames?.split("\n")?.last()
                this.posterUrl = "https://api.animeiat.co/storage/" + it.posterPath
            }
        }
        return if(request.name.contains("(H)")) HomePageResponse(
            arrayListOf(HomePageList(request.name.replace(" (H)",""), list, request.name.contains("(H)")))
        ) else newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val json = parseJson<All>(app.get("$mainUrl/anime?q=$query").text)
        return json.data.map {
            newAnimeSearchResponse(
                it.animeName.toString(),
                mainUrl + "/anime/" + it.slug.toString(),
                if(it.type == "movie") TvType.AnimeMovie else if(it.type == "tv") TvType.Anime else TvType.OVA,
            ) {
                addDubStatus(false, it.totalEpisodes)
                this.otherName = it.otherNames?.split("\n")?.last()
                this.posterUrl = "https://api.animeiat.co/storage/" + it.posterPath
            }
        }

    }

    data class Year (
        @JsonProperty("name"        ) var name        : String? = null,

    )
    data class Genres (
        @JsonProperty("name"        ) var name        : String? = null,
    )
    data class LoadData (
        @JsonProperty("anime_name"     ) var animeName     : String? = null,
        @JsonProperty("slug"           ) var slug          : String? = null,
        @JsonProperty("story"          ) var story         : String? = null,
        @JsonProperty("other_names"    ) var otherNames    : String? = null,
        @JsonProperty("age"            ) var age           : String? = null,
        @JsonProperty("type"           ) var type          : String? = null,
        @JsonProperty("status"         ) var status        : String? = null,
        @JsonProperty("poster_path"    ) var posterPath    : String? = null,
        @JsonProperty("year"           ) var year          : Year?              = Year(),
        @JsonProperty("genres"         ) var genres        : ArrayList<Genres>  = arrayListOf(),

    )
    data class Load (

        @JsonProperty("data" ) var data : LoadData? = LoadData()

    )
    data class Meta (
        @JsonProperty("last_page"    ) var lastPage    : Int? = null,
    )
    data class EpisodeData (
        @JsonProperty("title"        ) var title       : String? = null,
        @JsonProperty("slug"         ) var slug        : String? = null,
        @JsonProperty("number"       ) var number      : Int? = null,
        @JsonProperty("video_id"     ) var videoId     : Int? = null,
        @JsonProperty("poster_path"  ) var posterPath  : String? = null,
    )
    data class Episodes (
        @JsonProperty("data"  ) var data  : ArrayList<EpisodeData> = arrayListOf(),
        @JsonProperty("meta"  ) var meta  : Meta = Meta()
    )
    override suspend fun load(url: String): LoadResponse {
        Log.d("mainpageurl", "$url")
        val loadSession = Requests()
        val request = loadSession.get(url.replace(pageUrl, mainUrl)).text
        Log.d("Debug", "request: $request")
        val json = parseJson<Load>(request)
        Log.d("loadj", "request: $json")
        val episodes = arrayListOf<Episode>()
        (1..parseJson<Episodes>(loadSession.get("$url/episodes").text).meta.lastPage!!).map { pageNumber ->
            Log.d("Page", "$pageNumber")
            parseJson<Episodes>(loadSession.get("$url/episodes?page=$pageNumber").text).data.map {
                Log.d("episodes", "$episodes")
                Log.d("itepisodes", "$it")
                Log.d("Watchlinks", "$pageUrl/watch/"+it.slug)
                episodes.add(
                    Episode(
                        "$pageUrl/watch/"+it.slug,
                        it.title,
                        null,
                        it.number,
                        "https://api.animeiat.co/storage/" + it.posterPath,

                    )
                )
            }
        }
        Log.d("json", "$json")
        return newAnimeLoadResponse(json.data?.animeName.toString(), "$mainUrl/anime/"+json.data?.slug, if(json.data?.type == "movie") TvType.AnimeMovie else if(json.data?.type == "tv") TvType.Anime else TvType.OVA) {
            Log.d("Debug1", "japName: ${json.data?.otherNames?.replace("\\n.*".toRegex(), "")}")
            japName = json.data?.otherNames?.replace("\\n.*".toRegex(), "")

            Log.d("Debug2", "engName: ${json.data?.animeName}")
            engName = json.data?.animeName

            Log.d("Debug3", "posterUrl: https://api.animeiat.co/storage/${json.data?.posterPath}")
            posterUrl = "https://api.animeiat.co/storage/" + json.data?.posterPath

            Log.d("Debug4", "year: ${json.data?.year?.name?.toIntOrNull()}")
            this.year = json.data?.year?.name?.toIntOrNull()

            addEpisodes(DubStatus.Subbed, episodes)

            Log.d("Debug5", "plot: ${json.data?.story}")
            plot = json.data?.story

            Log.d("Debug6", "tags: ${json.data?.genres?.map { it.name.toString() }}")
            tags = json.data?.genres?.map { it.name.toString() }

            Log.d("Debug7", "showStatus: ${if(json.data?.status == "completed") ShowStatus.Completed else ShowStatus.Ongoing}")
            this.showStatus = if(json.data?.status == "completed") ShowStatus.Completed else ShowStatus.Ongoing
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val url = if(data.contains("-episode")) data else "$data-episode-1"
        Log.d("Debug8", "url: $url")

        val doc = app.get(url).document
        val script = doc.select("body > script").first()?.html()
        Log.d("Debug9", "script: $script")

        val id = script?.replace(".*4\",slug:\"|\",duration:.*".toRegex(),"")
        Log.d("Debug10", "id: $id")

        val player = app.get("$pageUrl/player/$id").document
        player.select("source").map {
            Log.d("Debug11", "source: ${it.attr("src")}, size: ${it.attr("size").toInt()}")
            callback.invoke(
                    ExtractorLink(
                            this.name,
                            this.name,
                            it.attr("src"),
                            pageUrl,
                            it.attr("size").toInt(),
                    )
            )
        }
        return true
    }
}