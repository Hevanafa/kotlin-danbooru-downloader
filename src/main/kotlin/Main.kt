import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

suspend fun fetchHtml(url: String) = GlobalScope.async {
    withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"

        conn.connect()

        val scanner = Scanner(conn.inputStream)
        val sb = StringBuilder()
        while (scanner.hasNextLine()) {
            val line = scanner.nextLine()
            sb.append(line)
        }

        sb.toString()

//        val bw = BufferedWriter(FileWriter("out.txt"))
//        bw.write(sb.toString())
//        bw.close()
    }
}

fun getImageFilename(imageUrl: String): String {
    return "_.*?\\.(jpg|png|webm)".toRegex().find(imageUrl)?.value ?: ""
}

// Todo: allow the usage of multiple artists
@OptIn(InternalAPI::class)
suspend fun downloadImage(imageUrl: String) = GlobalScope.async {
    if (!File("downloads").exists())
        withContext(Dispatchers.IO) {
            File("downloads").mkdir()
        }

    val filename = getImageFilename(imageUrl)

    val client = HttpClient(CIO)
    val res = client.get(imageUrl)

    if (!res.status.isSuccess())
        return@async

    val file = File("downloads\\$filename")
    res.content.copyAndClose(file.writeChannel())

    client.close()
}

@Composable
@Preview
fun App() {
    // Reformat code: Ctrl + Alt + L
    val defaultButtonText = "Download"

    var buttonText by remember { mutableStateOf(defaultButtonText)}
    var buttonDisabled by remember { mutableStateOf(false) }

    var url by remember { mutableStateOf("") }

    var artist by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }

    var outStream by remember { mutableStateOf("") }

    fun printOut(text: String?) {
        outStream += if (text == null) "\n" else "$text\n"
    }


    // Todo: provide a simple downloader interface for e621

    MaterialTheme {
        Scaffold {
            Column(
                Modifier.fillMaxWidth()
            ) {
                TextField(
                    // required
                    value = url,
                    label = { Text("URL") },
                    onValueChange = { url = it },

                    // optional
                    singleLine = true
                )

                Button(
                    enabled = !buttonDisabled,

                    onClick = {
                        buttonText = "Downloading..."
                        buttonDisabled = true

                        GlobalScope.launch {
                            try {
                                // reset output stream
                                outStream = ""

                                printOut("Fetching page...")

                                val html = fetchHtml(url).await()

                                printOut("Response size: ${html.length}")

                                // Ref: https://manserpatrice.medium.com/parse-html-with-jsoup-in-kotlin-69ab7fe4cb28
                                printOut("Parsing HTML...")

                                val doc = Jsoup.parse(html)
                                val artistTag = doc.select(".artist-tag-list .search-tag")

                                if (artistTag.size > 0)
                                    artist = artistTag[0].text()

                                val downloadTag =
                                    doc.select("#post-option-download > a") // doc.getElementById("post-option-download")

                                if (downloadTag.size > 0)
                                    imageUrl = downloadTag[0].attr("href")

                                printOut("Downloading image...")

                                downloadImage(imageUrl).await()

                                printOut("Image has been saved to \"downloads\\${getImageFilename(imageUrl)}\"")
                            } catch (ex: Exception) {
                                printOut(ex.message)
                            } finally {
                                buttonDisabled = false
                                buttonText = defaultButtonText
                            }
                        }
                    }
                ) { Text(buttonText) }

                TextField(
                    value = artist,
                    label = { Text("Artist") },
                    onValueChange = {},
                    readOnly = true
                )

                TextField(
                    value = imageUrl,
                    label = { Text("Full Image URL") },
                    onValueChange = {},
                    readOnly = true
                )

                Text(outStream)
            }
        }
    }
}

fun main() = application {
    Window(
        title = "Danbooru Downloader - By Hevanafa (24-07-2022)",
        onCloseRequest = ::exitApplication
    ) {
        App()
    }
}
