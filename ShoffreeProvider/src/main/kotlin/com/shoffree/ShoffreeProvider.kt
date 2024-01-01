package com.shoffree

import com.lagradost.cloudstream3.*

import com.lagradost.cloudstream3.utils.ExtractorLink

import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

import android.util.Log


class Shofree : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://shoffree.top"
    override var name = "Shoffree"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.value?.toIntOrNull()
    }


    private fun Element.toSearchResponse(home: Boolean): SearchResponse? {
        val quality = 0
        if (home == true) {
            val titleElement = select("div.Block--Info h3")
            Log.d("titleElement", "$titleElement")
            val typo = select("a")
            Log.d("typo", "$typo")
            val posterUrl = select("div.Poster--Block img").attr("data-src")
            Log.d("posterUrl", "$posterUrl")
            val tvType = if (typo.attr("href").contains("/movie/")) TvType.Movie else TvType.TvSeries

            return MovieSearchResponse(
                titleElement.text(),
                typo.attr("href"),
                this@Shofree.name,
                tvType,
                posterUrl,
                quality = getQualityFromString(quality.toString())
            )
        } else {
            val posterElement = select("div.Poster--Block img")
            val url = select("a").attr("href")
            Log.d("url", "$url")
            Log.d("posterElement.attr", posterElement.attr("alt"))
            return MovieSearchResponse(
                posterElement.attr("alt"),
                url,
                this@Shofree.name,
                if (url.contains("/movies/")) TvType.Movie else TvType.TvSeries,
                posterElement.attr("data-src"),
                quality = getQualityFromString(quality.toString())
            )
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/movies" to " Movies",
        "$mainUrl/series" to "Series",
        "$mainUrl/anime" to "Anime",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("ul.Blocks--List div.BlockItem").mapNotNull {
            Log.d("Home", "$it")
            it.toSearchResponse(true)
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/search?query=$query&search=").document
        return doc.select("ul.Blocks--List div.BlockItem").mapNotNull {
            Log.d("Search", "$it")
            it.toSearchResponse(false)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        Log.d("load URL", "$url")
        val doc = app.get(url).document
        val isMovie = url.contains("/movie/")

        val posterUrl = doc.select("div.p-0.rtl-pull-to-leading.col-md-3.col-lg-2.col-xl-2.align-m > img").attr("src")
        val rating = doc.select("div.singleRating b").text().split("/")[0].trim().toIntOrNull()
        val title = doc.select("div.d-flex.align-items-center div.px-0.py-2.text-justify.t-blackout-text-alternative-highlight.font-lg.text-center").text()
        val synopsis = doc.select("div.d-flex.w-auto p.t-blackout-text-alternative-highlight span.t-blackout-text-alternative").text()

        val tags = doc.select("div.d-flex div[class^=\"listOfTournaments-state-\"]")?.map { it.text() }

        val actors = doc.select("div.person").mapNotNull {
            val name = it.selectFirst("div > a > img")?.attr("alt") ?: return@mapNotNull null
            val image = it.selectFirst("div > a > img")?.attr("src") ?: return@mapNotNull null
            val roleString = it.select("div.data > div.caracter").text()
            val mainActor = Actor(name, image)
            ActorData(actor = mainActor, roleString = roleString)
        }

        return if (isMovie) {
            val recommendations = doc.select(".owl-item article").mapNotNull { element ->
                element.toSearchResponse(true)
            }

            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.plot = synopsis
                this.tags = tags
                this.actors = actors
                this.rating = rating
            }
        } else {
            val episodes = ArrayList<Episode>()

            val seasons = doc.select("div.carousel div.BlockItem a")
            seasons.forEachIndexed { seasonid, season ->
                val seasonUrl = season.attr("href")
                var seasonDocument = app.get(seasonUrl).document

                seasonDocument.select("ul.Blocks--List.episode div.BlockItem.episode").forEachIndexed { index,episodeElement ->
                    val episodeLink = episodeElement.select("a.sku")
                    val episodeUrl = episodeLink.attr("href")
                    val episodeTitle = episodeLink.attr("title")
                    val episodeSeasonNumber = seasonid + 1
                    val episodeIndex = episodeLink.select("span.episode i").text().getIntFromText()

                    episodes.add(
                        Episode(
                            episodeUrl,
                            episodeTitle,
                            episodeSeasonNumber,
                            episodeIndex
                        )
                    )
                }
            }


            val distinctEpisodes = episodes.distinct().sortedBy { it.episode }


            newTvSeriesLoadResponse(title, url, TvType.TvSeries, distinctEpisodes) {
                this.duration = duration
                this.posterUrl = posterUrl
                this.year = year
                this.plot = synopsis
                this.tags = tags
            }
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("LOADLINKSDATA", "$data")
        val doc = app.get(data).document
        val extractedNumber = Regex("""/(\d+)/""").find(data)?.groups?.get(1)?.value?.toIntOrNull()
            ?: -1
        val extractedNumberep = Regex("""/episode/(\d+)/""").find(data)?.groups?.get(1)?.value?.toIntOrNull()

        if(data.contains("/episode/")){
            val watchLinkDocument = app.get("$mainUrl/stream/$extractedNumberep/episode").document

            Log.d("extractedNumberep", "$extractedNumberep")

            val scriptElement = watchLinkDocument.select("script#shoffree").firstOrNull()
            Log.d("scriptElement", "$scriptElement")
            val scriptContent = scriptElement?.data()
            Log.d("scriptContent", "$scriptContent")

            val filePattern = """file:\s*"([^"]*)"""".toRegex()
            val labelPattern = """label:\s*"([^"]*)"""".toRegex()

            val fileMatches = filePattern.findAll(scriptContent ?: "")
            val labelMatches = labelPattern.findAll(scriptContent ?: "")

            val files = fileMatches.map { it.groupValues[1] }.toList()
            val labels = labelMatches.map { it.groupValues[1] }.toList()

            for (i in files.indices) {
                val fileUrl = files[i]
                val label = labels[i]
                val cleanedLabel = when {
                    label.equals("xp", ignoreCase = true) -> "0"
                    label.endsWith("p", ignoreCase = true) -> label.dropLast(1)
                    else -> label
                }

                Log.d("fileUrl", "$label + $fileUrl")

                val quality = cleanedLabel.toIntOrNull() ?: -1

                loadExtractor(fileUrl, mainUrl, subtitleCallback, callback)

                callback.invoke(
                    ExtractorLink(
                        this.name,
                        "Shoofreee",
                        fileUrl,
                        data,
                        quality
                    )
                )
            }

            return loadExtractor("$mainUrl/stream/$extractedNumberep/episode", mainUrl, subtitleCallback, callback)
        }



        Log.d("extractedNumber", "$extractedNumber")

        val watchLinkDocument = app.get("$mainUrl/stream/$extractedNumber/movie").document

        val scriptElement = watchLinkDocument.select("script#shoffree").firstOrNull()
        Log.d("scriptElement", "$scriptElement")
        val scriptContent = scriptElement?.data()
        Log.d("scriptContent", "$scriptContent")

        val filePattern = """file:\s*"([^"]*)"""".toRegex()
        val labelPattern = """label:\s*"([^"]*)"""".toRegex()

        val fileMatches = filePattern.findAll(scriptContent ?: "")
        val labelMatches = labelPattern.findAll(scriptContent ?: "")

        val files = fileMatches.map { it.groupValues[1] }.toList()
        val labels = labelMatches.map { it.groupValues[1] }.toList()

        for (i in files.indices) {
            val fileUrl = files[i]
            val label = labels[i]
            val cleanedLabel = when {
                label.equals("xp", ignoreCase = true) -> "0"
                label.endsWith("p", ignoreCase = true) -> label.dropLast(1)
                else -> label
            }

            Log.d("fileUrl", "$label + $fileUrl")

            val quality = cleanedLabel.toIntOrNull() ?: -1

            loadExtractor(fileUrl, mainUrl, subtitleCallback, callback)

            callback.invoke(
                ExtractorLink(
                    this.name,
                    "Shoofreee",
                    fileUrl,
                    data,
                    quality
                )
            )
        }

        return loadExtractor("$mainUrl/stream/$extractedNumber/movie", mainUrl, subtitleCallback, callback)
    }
}