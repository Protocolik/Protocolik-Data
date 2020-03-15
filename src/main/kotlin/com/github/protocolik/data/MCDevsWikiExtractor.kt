package com.github.protocolik.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.jsoup.Jsoup
import java.io.File
import java.net.URL
import java.util.*

val ROOT_DIR = if (File("Protocolik-Data").exists()) File("Protocolik-Data") else File("")
val GSON = GsonBuilder().setPrettyPrinting().create()

fun main() {
    val versions = ProtocolVersionsNumbers.parse()
    val protocolJavaVersionsJson = JsonObject()
    versions.forEach {
        protocolJavaVersionsJson.addProperty(it.releaseName, it.versionNumber)
    }
    File(ROOT_DIR, "protocol_java_versions.json").writeText(GSON.toJson(protocolJavaVersionsJson))
}

object ProtocolVersionsNumbers {
    data class Version(
            val releaseName: String,
            val versionNumber: Int,
            val lastKnownDocumentation: URL?
    )

    fun parse(): List<Version> {
        val document = Jsoup.connect("https://wiki.vg/Protocol_version_numbers").get()
        val select = document.select("tbody")
        val versions = LinkedList<Version>()
        var lastVersion: Version? = null
        select.forEach {
            it.select("tr").forEach {
                val columns = it.select("td").toList()
                if (columns.isNotEmpty()) {
                    val displayName = columns[0].text()
                    val version = (if (columns.size >= 2) columns[1].text().trim().split(" ").first().toIntOrNull() else null)
                            ?: lastVersion!!.versionNumber
                    val rawUrl = if (columns.size >= 3) columns[2].select("a").attr("href") else ""
                    val url = if (rawUrl.startsWith("/")) {
                        URL("https://wiki.vg$rawUrl")
                    } else if (rawUrl.isNotEmpty() && rawUrl.startsWith("https://")) {
                        URL(rawUrl)
                    } else {
                        lastVersion!!.lastKnownDocumentation
                    }
                    lastVersion = Version(displayName, version, url)
                    versions.add(lastVersion!!)
                }
            }
        }
        return versions
    }
}


private fun getWikiSource(pageTitle: String): String? {
    val document = Jsoup.connect("https://wiki.vg/index.php?title=$pageTitle&action=edit").get()
    val select = document.select("textarea")
    return select.text()
}