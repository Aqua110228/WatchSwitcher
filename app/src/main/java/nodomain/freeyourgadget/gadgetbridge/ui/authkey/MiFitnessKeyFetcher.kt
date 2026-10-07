/*  Copyright (C) 2026 WatchSwitcher contributors

    This file is part of WatchSwitcher, based on Gadgetbridge.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.  */
package nodomain.freeyourgadget.gadgetbridge.ui.authkey

import android.util.Base64
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

data class MiDevice(val name: String, val mac: String, val authKey: String)

class MiFitnessException(message: String) : Exception(message)

/**
 * Fetches the Bluetooth auth key of Xiaomi / Mi Fitness bound devices from Xiaomi's servers.
 * Reimplements the login + encrypted request flow used by the community "huami-token" tool
 * (MIT). Only ever talks to Xiaomi's own account/health endpoints with the user's own account.
 */
class MiFitnessKeyFetcher(private val region: String = "cn") {

    private companion object {
        const val UA = "Android-12-9.8.348i-google-Pixel 4"
        const val SERVICE_LOGIN = "https://account.xiaomi.com/pass/serviceLogin"
        const val SERVICE_LOGIN_AUTH2 = "https://account.xiaomi.com/pass/serviceLoginAuth2"
        const val SOURCE_LIST = "https://hlth.io.mi.com/app/v1/source/get_source_list"
        const val B64 = Base64.NO_WRAP
        const val PREFIX = "&&&START&&&"
    }

    private val client = OkHttpClient.Builder().followRedirects(true).build()
    private val noRedirectClient = OkHttpClient.Builder().followRedirects(false).build()

    private var ssecurity = ""
    private var serviceToken = ""
    private var cUserId = ""
    private var userId = ""
    private lateinit var deviceId: String

    @Throws(MiFitnessException::class)
    fun fetch(email: String, password: String): List<MiDevice> {
        deviceId = "an_" + md5Hex(email)
        login(email, password)
        return getSourceList()
    }

    private fun login(email: String, password: String) {
        val (sign, qs, callback) = getLoginParams(email)
        authenticate(email, password, sign, qs, callback)
    }

    private fun getLoginParams(email: String): Triple<String, String, String> {
        val url = "$SERVICE_LOGIN?_json=true&sid=miothealth&_locale=en_US"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; wv) AppleWebKit/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .header("Cookie", "userId=$email; deviceId=$deviceId")
            .get()
            .build()
        val text = execute(request)
        val json = JSONObject(stripPrefix(text))
        val sign = json.optString("_sign")
        val qs = json.optString("qs")
        val callback = json.optString("callback")
        if (sign.isEmpty() || qs.isEmpty() || callback.isEmpty()) {
            throw MiFitnessException("无法获取登录参数（_sign/qs/callback）")
        }
        return Triple(sign, qs, callback)
    }

    private fun authenticate(email: String, password: String, sign: String, qs: String, callback: String) {
        val hash = md5Hex(password).uppercase()
        val body = FormBody.Builder()
            .add("qs", qs)
            .add("callback", callback)
            .add("_json", "true")
            .add("_sign", sign)
            .add("user", email)
            .add("hash", hash)
            .add("sid", "miothealth")
            .add("_locale", "en_US")
            .build()
        val request = Request.Builder()
            .url(SERVICE_LOGIN_AUTH2)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; wv) AppleWebKit/537.36")
            .header("Cookie", "deviceId=$deviceId")
            .post(body)
            .build()
        val json = JSONObject(stripPrefix(execute(request)))
        if (json.optInt("code", -1) != 0) {
            throw MiFitnessException("登录失败：${json.optString("description", json.optString("code"))}")
        }
        ssecurity = json.optString("ssecurity")
        cUserId = json.optString("cUserId")
        userId = json.optString("userId")
        val nonce = json.optString("nonce")
        val location = json.optString("location")
        if (ssecurity.isEmpty() || location.isEmpty()) {
            throw MiFitnessException("登录响应缺少 ssecurity/location")
        }
        val signInput = "nonce=$nonce&$ssecurity"
        val clientSign = b64Encode(MessageDigest.getInstance("SHA-1").digest(signInput.toByteArray()))
        val tokenRequest = Request.Builder()
            .url("$location&clientSign=" + java.net.URLEncoder.encode(clientSign, "UTF-8"))
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; wv) AppleWebKit/537.36")
            .get()
            .build()
        val response = noRedirectClient.newCall(tokenRequest).execute()
        response.use { resp ->
            serviceToken = resp.header("Set-Cookie")
                ?.split(";")
                ?.firstOrNull { it.trim().startsWith("serviceToken=") }
                ?.substringAfter("=")
                .orEmpty()
        }
        if (serviceToken.isEmpty()) {
            throw MiFitnessException("未能获取 serviceToken")
        }
    }

    private fun getSourceList(): List<MiDevice> {
        val data = "{\"page_size\":50,\"status\":1}"
        val nonce = generateNonce()
        val encrypted = encryptParams(
            method = "POST",
            signingPath = "/app/v1/source/get_source_list",
            params = mapOf("data" to data),
            nonceB64 = nonce,
            ssecurityB64 = ssecurity,
        )
        val bodyBuilder = FormBody.Builder()
        encrypted.forEach { (k, v) -> bodyBuilder.add(k, v) }
        val request = Request.Builder()
            .url(SOURCE_LIST)
            .header("User-Agent", UA)
            .header("region_tag", region)
            .header("Cookie", "cUserId=$cUserId; serviceToken=$serviceToken; locale=en_us")
            .post(bodyBuilder.build())
            .build()
        val text = execute(request)
        val decrypted = decryptResponse(text, nonce, ssecurity)
        return parseDevices(JSONObject(decrypted))
    }

    private fun parseDevices(json: JSONObject): List<MiDevice> {
        val list = json.optJSONObject("result")?.optJSONArray("list") ?: return emptyList()
        val devices = mutableListOf<MiDevice>()
        for (i in 0 until list.length()) {
            val source = list.optJSONObject(i) ?: continue
            val name = source.optString("name", "Unknown").trim()
            var detail = source.opt("detail")
            if (detail is String) {
                detail = runCatching { JSONObject(detail) }.getOrNull()
            }
            val detailObj = detail as? JSONObject
            val mac = detailObj?.optString("mac")?.takeIf { it.isNotBlank() }
                ?: source.optString("mac")
            val authKey = detailObj?.optString("auth_key").orEmpty()
            if (mac.isNotBlank() && authKey.isNotBlank()) {
                devices.add(MiDevice(name, mac, authKey))
            }
        }
        return devices
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful && body.isBlank()) {
                throw MiFitnessException("请求失败：HTTP ${response.code}")
            }
            return body
        }
    }

    private fun stripPrefix(text: String): String =
        if (text.startsWith(PREFIX)) text.substring(PREFIX.length) else text

    // --- crypto (mirrors huami-token's mi_crypto.py) ---

    private class Rc4(key: ByteArray) {
        private val s = IntArray(256) { it }
        private var i = 0
        private var j = 0

        init {
            var jj = 0
            for (idx in 0..255) {
                jj = (jj + s[idx] + (key[idx % key.size].toInt() and 0xFF)) and 0xFF
                val t = s[idx]; s[idx] = s[jj]; s[jj] = t
            }
        }

        fun crypt(data: ByteArray): ByteArray {
            val out = ByteArray(data.size)
            for (idx in data.indices) {
                i = (i + 1) and 0xFF
                j = (j + s[i]) and 0xFF
                val t = s[i]; s[i] = s[j]; s[j] = t
                out[idx] = (data[idx].toInt() xor s[(s[i] + s[j]) and 0xFF]).toByte()
            }
            return out
        }
    }

    private fun makeRc4(keyB64: String): Rc4 {
        val rc4 = Rc4(b64Decode(keyB64))
        rc4.crypt(ByteArray(1024))
        return rc4
    }

    private fun deriveRc4Key(ssecurityB64: String, nonceB64: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(b64Decode(ssecurityB64))
        md.update(b64Decode(nonceB64))
        return b64Encode(md.digest())
    }

    private fun generateNonce(): String {
        val randomPart = ByteArray(8).also { Random.nextBytes(it) }
        val minutes = (System.currentTimeMillis() / 60000L).toInt()
        val timePart = byteArrayOf(
            ((minutes ushr 24) and 0xFF).toByte(),
            ((minutes ushr 16) and 0xFF).toByte(),
            ((minutes ushr 8) and 0xFF).toByte(),
            (minutes and 0xFF).toByte(),
        )
        return b64Encode(randomPart + timePart)
    }

    private fun sha1Sign(
        method: String,
        path: String,
        params: Map<String, String>,
        rc4KeyB64: String,
    ): String {
        val parts = mutableListOf<String>()
        parts.add(method.uppercase())
        parts.add(path)
        params.keys.sorted().forEach { key -> parts.add("$key=${params[key]}") }
        parts.add(rc4KeyB64)
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(parts.joinToString("&").toByteArray(Charsets.UTF_8))
        return b64Encode(digest)
    }

    @Suppress("unused")
    private fun hmacSign(message: String, rc4KeyB64: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(b64Decode(rc4KeyB64), "HmacSHA256"))
        return b64Encode(mac.doFinal(message.toByteArray(Charsets.UTF_8)))
    }

    private fun encryptParams(
        method: String,
        signingPath: String,
        params: Map<String, String>,
        nonceB64: String,
        ssecurityB64: String,
    ): LinkedHashMap<String, String> {
        val rc4KeyB64 = deriveRc4Key(ssecurityB64, nonceB64)
        val plain = params.toSortedMap()
        plain["rc4_hash__"] = sha1Sign(method, signingPath, plain, rc4KeyB64)
        val sorted = plain.toSortedMap()

        val rc4 = makeRc4(rc4KeyB64)
        val encrypted = LinkedHashMap<String, String>()
        for (key in sorted.keys.sorted()) {
            encrypted[key] = b64Encode(rc4.crypt(sorted.getValue(key).toByteArray(Charsets.UTF_8)))
        }
        val output = LinkedHashMap(encrypted)
        output["signature"] = sha1Sign(method, signingPath, encrypted, rc4KeyB64)
        output["_nonce"] = nonceB64
        return output
    }

    private fun decryptResponse(bodyB64: String, nonceB64: String, ssecurityB64: String): String {
        val rc4KeyB64 = deriveRc4Key(ssecurityB64, nonceB64)
        return String(makeRc4(rc4KeyB64).crypt(b64Decode(bodyB64)), Charsets.UTF_8)
    }

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun b64Encode(bytes: ByteArray): String = Base64.encodeToString(bytes, B64)
    private fun b64Decode(value: String): ByteArray = Base64.decode(value, B64)
}
