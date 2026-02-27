package com.trojan.autoscreenshot

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var saveButton: Button
    private lateinit var sendButton: Button

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        saveButton = findViewById(R.id.saveButton)
        sendButton = findViewById(R.id.sendButton)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        urlInput.setText(prefs.getString("server_url", ""))

        saveButton.setOnClickListener {
            prefs.edit().putString("server_url", urlInput.text.toString()).apply()
            Toast.makeText(this, "URL Saved", Toast.LENGTH_SHORT).show()
        }

        sendButton.setOnClickListener {
            val url = prefs.getString("server_url", null)

            if (url.isNullOrEmpty()) {
                Toast.makeText(this, "Enter URL first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendPlaceholder(url)
        }
    }

    private fun sendPlaceholder(url: String) {

        val json = """
            {
                "message": "placeholder_data",
                "timestamp": "${System.currentTimeMillis()}"
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Success", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}