package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private val apiKey = "AIzaSyBlg8m00S1zq3l5XwXBsw8OYpu_9zrEnbc"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Firebase.auth.signInAnonymously().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e("FirebaseAuth", "Erro ao autenticar: ${task.exception?.message}")
            }
        }

        val btnEnviar = findViewById<Button>(R.id.btnEnviar)
        val perguntarIA = findViewById<EditText>(R.id.perguntarIA)
        val resGemini = findViewById<TextView>(R.id.resGemini)

        btnEnviar.setOnClickListener {
            val ingredientes = perguntarIA.text.toString()

            // Prompt formatado
            val prompt = """
                Me responda no seguinte formato:

                Nome criativo com tema romântico

                Receita:
                Passo a passo criativo


                [Frase romântica para Instagram]

                Ingredientes: $ingredientes
            """.trimIndent()

            perguntaGemini(prompt) { resposta ->
                runOnUiThread {
                    resGemini.text = resposta
                }

                // Salvar no Firebase após a resposta
                salvarReceitaNoFirebase(ingredientes, resposta)
            }
        }
    }

    private fun perguntaGemini(pergunta: String, callback: (String) -> Unit) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

        val json = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", pergunta)
                ))
            ))
        }

        val mediaType = "application/json".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Erro: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val resposta = response.body?.string() ?: "Sem resposta"
                try {
                    val jsonResposta = JSONObject(resposta)
                    val texto = jsonResposta
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    callback(texto)
                } catch (e: Exception) {
                    callback("Erro ao ler resposta: ${e.message}")
                }
            }
        })
    }

    private fun salvarReceitaNoFirebase(ingredientes: String, resposta: String) {
        val database = Firebase.database
        val userId = Firebase.auth.currentUser?.uid ?: "anon"
        val receitasRef = database.getReference("receitas").child(userId)

        val receitaData = mapOf(
            "ingredientes" to ingredientes,
            "resposta" to resposta,
            "timestamp" to System.currentTimeMillis()
        )

        receitasRef.push().setValue(receitaData)
    }
}