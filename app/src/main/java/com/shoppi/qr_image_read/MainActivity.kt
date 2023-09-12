package com.shoppi.qr_image_read

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.load
import com.google.zxing.integration.android.IntentIntegrator

class MainActivity : AppCompatActivity() {
    companion object {
        const val PERMISSION_REQUEST_CODE = 100
        const val RETRY_COUNT = 5
    }

    private lateinit var webView: WebView
    private lateinit var imageView: ImageView

    private var downloadID: Long = 0
    private val onDownloadComplete = DownloadCompleteReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        checkPermissions()
        scanQRCode()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        imageView = findViewById(R.id.imageView)
    }

    private fun scanQRCode() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Scan a QR code")
        integrator.setCameraId(0)  // Use the default camera
        integrator.setBeepEnabled(false)
        integrator.setBarcodeImageEnabled(true)
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Log.d("MainActivity", "Cancelled scan")
                finish()  // 스캔이 취소되면 앱을 종료
            } else {
                Log.d("MainActivity", "Scanned: " + result.contents)
                setupWebView()  // 웹뷰 설정
                webView.loadUrl(result.contents)  // 스캔된 URL 로드
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun setupWebView() {
        configureWebViewSettings()
        setWebViewClient()
        setDownloadListener()
        webView.loadUrl("https://life4cut-l4c01.s3-accelerate.amazonaws.com/web/web/QRImage/20230904/SEL.YSN.MYUNG02/202003232/index.html")
    }

    private fun configureWebViewSettings() {
        webView.settings.apply {
            loadWithOverviewMode = true
            useWideViewPort = true
            domStorageEnabled = true
            javaScriptEnabled = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }
    }

    private fun setWebViewClient() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                attemptClickDownloadButton()
            }
        }
    }

    private fun attemptClickDownloadButton(retries: Int = RETRY_COUNT) {
        if (retries <= 0) return

        // 이미지 다운로드 버튼만 클릭하려고 시도
        val jsScript = """(function() {
            var imageDownloadButton = document.querySelector('a[id="image_download"], a[href$=".jpg"][download], a.btn_photodn');
            if (imageDownloadButton) {
                imageDownloadButton.click();
                return true;
            }
            return false;
        })();"""
        webView.evaluateJavascript(jsScript, ValueCallback { success ->
            if (success == "false") {
                // 버튼을 찾지 못한 경우 1초 후에 다시 시도
                webView.postDelayed({ attemptClickDownloadButton(retries - 1) }, 1000)
            }
        })
    }

    private fun setDownloadListener() {
        webView.setDownloadListener { url, _, _, _, _ ->
            handleDownloadRequest(url)
        }
    }

    private fun handleDownloadRequest(url: String) {
        if (url.endsWith(".jpg")) {  // 이미지 URL만 처리
            Log.d("DownloadImage", "Download request received for URL: $url")
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType("image/jpeg")

            val cookies = CookieManager.getInstance().getCookie(url)
            request.addRequestHeader("cookie", cookies)
            request.addRequestHeader("User-Agent", webView.settings.userAgentString)
            request.setDescription("Downloading image...")
            request.setTitle(URLUtil.guessFileName(url, null, "image/jpeg"))
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                URLUtil.guessFileName(url, null, "image/jpeg")
            )
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadID = dm.enqueue(request)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(onDownloadComplete)
    }

    inner class DownloadCompleteReceiver : BroadcastReceiver() {
        @SuppressLint("Range")
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("DownloadImage", "Download completed. Checking the file...")
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadID == id) {
                val query = DownloadManager.Query()
                query.setFilterById(downloadID)
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val cursor = manager.query(query)
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                        val uri =
                            Uri.parse(cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)))
                        Log.d("DownloadImage", "Local URI of the downloaded image: $uri")
                        imageView.load(uri)
                    } else {
                        Log.e("DownloadImage", "Download was not successful")
                    }
                }
                cursor.close()
            }
        }
    }
}
