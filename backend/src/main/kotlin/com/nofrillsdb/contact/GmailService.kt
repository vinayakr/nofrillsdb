package com.nofrillsdb.contact

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayOutputStream
import java.util.*
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

@Service
class GmailService(
    @Value("\${gmail.clientId}") private val clientId: String,
    @Value("\${gmail.clientSecret}") private val clientSecret: String,
    @Value("\${gmail.refreshToken}") private val refreshToken: String,
) {
    private val client = OkHttpClient()
    private val mapper = jacksonObjectMapper()
    private val sender = "Vinayak Rao (No Frills DB) <vinayakr@nofrillsdb.com>"

    private fun accessToken(): String {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()

        val req = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(form)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Token refresh failed: ${resp.code} $body")
            return mapper.readTree(body)["access_token"].asText()
        }
    }

    fun sendContactEmail(replyTo: String, name: String, messageText: String) {
        val html = """
      <p><b>From:</b> ${escape(name)} (${escape(replyTo)})</p>
      <p>${escape(messageText).replace("\n","<br/>")}</p>
    """.trimIndent()

        val raw = buildRawEmail(
            from = sender,
            to = sender,
            replyTo = replyTo,
            subject = "Website contact form",
            htmlBody = html
        )

        val json = mapper.writeValueAsString(mapOf("raw" to raw))
        val reqBody = json.toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
            .addHeader("Authorization", "Bearer ${accessToken()}")
            .post(reqBody)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Send failed: ${resp.code} $body")
        }
    }

    private fun buildRawEmail(from: String, to: String, replyTo: String, subject: String, htmlBody: String): String {
        val session = Session.getInstance(Properties())
        val msg = MimeMessage(session).apply {
            setFrom(InternetAddress(from))
            setRecipients(javax.mail.Message.RecipientType.TO, InternetAddress.parse(to))
            setReplyTo(arrayOf(InternetAddress(replyTo)))
            setSubject(subject, "UTF-8")
            setContent(htmlBody, "text/html; charset=UTF-8")
        }
        val out = ByteArrayOutputStream()
        msg.writeTo(out)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    private fun escape(s: String) =
        s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
}