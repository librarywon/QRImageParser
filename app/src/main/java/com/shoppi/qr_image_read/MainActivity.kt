package com.shoppi.qr_image_read

import android.os.Bundle
import android.webkit.URLUtil
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import java.net.URL


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val textView = findViewById<TextView>(R.id.tv_url)
        val imageView = findViewById<ImageView>(R.id.iv_qr_image)
        // QR로 스캔 했다고 치고 받아온 URL
        val scannedUrl =
           ""
        // 비동기 시작
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = Jsoup.connect(scannedUrl).get()
                val downloadLinks = doc.select("a[download]")
                var imageUrl: String? = null

                for (link in downloadLinks) {
                    var url = link.attr("href")
                    //경로가 상대경로라면 절대경로로 변경
                    if (!URLUtil.isValidUrl(url)) {
                        val baseUrl = URL(scannedUrl)
                        url = URL(baseUrl, url).toString()
                    }
                    //jpg,png 확장자만 받아내고 중지
                    if (url.endsWith(".jpg") || url.endsWith(".png")) {
                        imageUrl = url
                        break
                    }
                }
                //mapping
                CoroutineScope(Dispatchers.Main).launch {
                    textView.text = imageUrl
                    imageView.load(imageUrl)
                }
            } catch (e: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }
}