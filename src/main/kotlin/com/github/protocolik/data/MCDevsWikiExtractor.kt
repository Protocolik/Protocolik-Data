package com.github.protocolik.data

import com.github.protocolik.api.protocol.PacketType
import com.github.protocolik.api.protocol.ProtocolDirection
import com.github.protocolik.api.protocol.ProtocolState
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.net.URL
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.HashMap

val ROOT_DIR = if (File("Protocolik-Data").exists()) File("Protocolik-Data") else File("")
val GSON = GsonBuilder().setPrettyPrinting().create()

fun main() {
    parse()
}

fun parse() {
    val versions = ProtocolVersionsNumbers.parse()

    val protocolJavaVersionsJson = JsonObject()
    versions.forEach {
        protocolJavaVersionsJson.addProperty(it.releaseName, it.versionNumber)
        println(it)
    }
    File(ROOT_DIR, "protocol_java_versions.json").writeText(GSON.toJson(protocolJavaVersionsJson))

    var nettyPackets = true
    val dispatcher = Executors.newFixedThreadPool(12).asCoroutineDispatcher()
    val asyncPackets = versions.map {
        GlobalScope.async(dispatcher) {
            val url = it.lastKnownDocumentation
            if (url != null && nettyPackets) {
                try {
                    if (it.versionNumber == 0) {
                        nettyPackets = false
                    }
                    ProtocolPackets(it, url).parse().also {
                        it.saveNames()
                        PacketMappings.parse(it)
                    }
                } catch (e: Exception) {
                    throw Exception("Error parsing $url", e)
                }
            } else {
                emptyList()
            }
        }
    }
    runBlocking {
        asyncPackets.awaitAll().forEach {

        }
    }
}

object PacketMappings {
    val file = File(ROOT_DIR, "packets_java_versions.json")

    @Synchronized
    fun parse(list: List<ProtocolPackets.PacketInfo>) {
        val json = if (!file.exists()) JsonObject() else JsonParser.parseReader(file.reader()).asJsonObject
        for (packetInfo in list) {
            val packetType = packetInfo.packetType
            if (packetType != null) {
                val jsonPacket = if (json.has(packetType.name.toLowerCase())) json.getAsJsonObject(packetType.name.toLowerCase()) else JsonObject()
                jsonPacket.addProperty(packetInfo.protocolVersion.releaseName, packetInfo.packetId)
                json.add(packetType.name.toLowerCase(), jsonPacket)
            }
        }
        file.writeText(GSON.toJson(json))
    }
}

@Synchronized
private fun List<ProtocolPackets.PacketInfo>.saveNames() {
    val mcDevsWikiPacketNamesJson = File(ROOT_DIR, "mc_devs_wiki_packet_names.json").also { file ->
        val json = if (file.exists()) JsonParser.parseReader(file.reader()).asJsonObject else JsonObject()
        val map = HashMap<String, PacketType?>()
        val packetTypeValues = PacketType.values()
        for (entry in json.entrySet()) {
            map[entry.key] = packetTypeValues.find { it.name == entry.value.asString.toUpperCase() }
        }
        for (packet in this) {
            val packetName = packet.packetName
            val packetKey = packetName.toUpperCase()
            val type = packetTypeValues.find { it.name == packetKey }
            if (!map.containsKey(packetName) || map[packetName] == null) {
                if (type != null) {
                    map[packetName] = type
                } else {
                    map[packetName] = null
                    println("(${packet.protocolVersion.versionNumber}) Unknown type for name: $packetName [${packet.packetId}] - $packetKey???")
                }
            }
            packet.packetType = type
        }
        map.forEach { (key, value) ->
            val v = value?.name?.toLowerCase() ?: ""
            if (key != v) {
                json.addProperty(key, v)
            }
        }
        file.writeText(GSON.toJson(json))
    }
}

class ProtocolPackets(val protocolVersion: ProtocolVersionsNumbers.Version, val url: URL) {
    val packets = LinkedList<PacketInfo>()
    var currentProtocolState: ProtocolState? = null
    var currentProtocolDirection: ProtocolDirection? = null

    data class PacketInfo(
            val protocolVersion: ProtocolVersionsNumbers.Version,
            val protocolState: ProtocolState,
            val protocolDirection: ProtocolDirection,
            val packetId: String,
            val packetName: String,
            var packetType: PacketType? = null
    )

    fun parse(): List<PacketInfo> {
        println("Parsing $protocolVersion")
        val document = Jsoup.connect(url.toString()).get()
        val title = document.selectFirst("h1").text()
        if (title.equals("Pre-release protocol", true)) {
            document.parsePreReleaseProtocol()
        }
        if (title.equals("Protocol", true)) {
            document.parseProtocol()
        }
        return packets
    }

    fun Document.parseProtocol() {
        var currentPacketName: String? = null
        iterator@ for (element in selectFirst("div#mw-content-text").allElements.toList()) {
//            println("element=$element")
            if (element.`is`("h2")) {
                currentProtocolState = when (element.selectFirst("span")?.text() ?: "") {
                    "Handshaking" -> ProtocolState.HANDSHAKING
                    "Status" -> ProtocolState.STATUS
                    "Login" -> ProtocolState.LOGIN
                    "Play" -> ProtocolState.PLAY
                    else -> continue@iterator
                }
//                println("ProtocolState = $currentProtocolState")
            }
            if (element.`is`("h3")) {
                currentProtocolDirection = when (element.selectFirst("span")?.text() ?: "") {
                    "Clientbound" -> ProtocolDirection.CLIENTBOUND
                    "Serverbound" -> ProtocolDirection.SERVERBOUND
                    else -> continue@iterator
                }
//                println("ProtocolDirection=$currentProtocolDirection")
            }
            if (element.`is`("h4")) {
                currentPacketName = currentProtocolState!!.name.toLowerCase() + "_" + currentProtocolDirection!!.name.toLowerCase() + "_" + (element.selectFirst("span")?.text()
                        ?: "").formatPacketName()
//                println("PacketName=$currentPacketName")
            }
            if (element.`is`("table") && currentPacketName != null) {
                try {
                    val tableR = element.select("tr").toList()
                    if (tableR.size >= 2 && tableR[0].text().startsWith("Packet ID")) {
                        val tableD = tableR[1].select("td").toList()
                        val packetId = tableD.first().text().trim()
                        val packetInfo = PacketInfo(
                                protocolVersion,
                                currentProtocolState!!,
                                currentProtocolDirection!!,
                                packetId,
                                currentPacketName
                        )
                        packets.add(packetInfo)
//                        println("Packet=$packetInfo")
                    }
                } finally {
                    currentPacketName = null
                }
            }
        }
    }

    fun Document.parsePreReleaseProtocol() {
        this.select("tbody").forEach { tableBody ->
            val tableRows = tableBody.select("tr").toList()
            try {
                var tableH = tableRows.first().select("th").toList()
                if (tableH.size >= 2 && tableH[1].text().contains("Packet name", true)) {
                    tableRows.forEach { tableRow ->
                        tableH = tableRow.select("th").toList()
//                    println("TABLEH(${tableH.size}) = $tableH")
                        if (tableH.size == 1) {
                            val text = tableH.first().text()
                            if (text.contains("serverbound", true)) {
                                currentProtocolDirection = ProtocolDirection.SERVERBOUND
                            }
                            if (text.contains("clientbound", true)) {
                                currentProtocolDirection = ProtocolDirection.CLIENTBOUND
                            }
                            if (text.contains("handshaking", true)) {
                                currentProtocolState = ProtocolState.HANDSHAKING
                            }
                            if (text.contains("status", true)) {
                                currentProtocolState = ProtocolState.STATUS
                            }
                            if (text.contains("login", true)) {
                                currentProtocolState = ProtocolState.LOGIN
                            }
                            if (text.contains("play", true)) {
                                currentProtocolState = ProtocolState.PLAY
                            }
                        }

                        val tableD = tableRow.select("td").toList()
//                    println("TABLED(${tableD.size}) = $tableD")
                        if (tableD.isNotEmpty()) {
                            val packetId = tableD[0].text().trim().split(" ").last()
                            val packetName = currentProtocolState!!.name.toLowerCase() + "_" + currentProtocolDirection!!.name.toLowerCase() + "_" + tableD[1].text().formatPacketName()
                            val packetInfo = PacketInfo(
                                    protocolVersion,
                                    currentProtocolState!!,
                                    currentProtocolDirection!!,
                                    packetId,
                                    packetName
                            )
                            packets.add(packetInfo)
//                            println("PACKET=$packetInfo")
                        }
                    }
                }
            } catch (e: Exception) {
                throw java.lang.Exception("Error parsing table: $tableRows", e)
            }
        }
    }

    fun String.formatPacketName() = toLowerCase()
            .replace(" (clientbound)", "")
            .replace(" (serverbound)", "")
            .trim()
            .replace("(", "")
            .replace(")", "")
            .replace(" ", "_")
            .replace("-", "_")
            .replace("/", "_")
            .replace("?", "").also {
                if (it.endsWith("_")) {
                    error("_ found: '$it' '$this'")
                }
            }

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

