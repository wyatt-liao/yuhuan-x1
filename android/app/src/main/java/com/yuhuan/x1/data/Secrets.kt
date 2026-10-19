package com.yuhuan.x1.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only the Wi-Fi bearer token is persisted here. SSID passwords are never stored by the app. */
class Secrets(context: Context) {
    private val prefs=context.getSharedPreferences("connection",Context.MODE_PRIVATE)
    private val alias="yuhuan-x1-token-v1"
    var address: String
        get()=prefs.getString("address","")?:""
        set(value) { prefs.edit().putString("address",value).apply() }
    var auto: Boolean
        get()=prefs.getBoolean("auto",true)
        set(value) { prefs.edit().putBoolean("auto",value).apply() }
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun saveToken(address: String, token: String) {
        require(token.matches(Regex("[0-9a-f]{64}")))
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val encoded=Base64.encodeToString(cipher.iv+cipher.doFinal(token.toByteArray()),Base64.NO_WRAP)
        prefs.edit().putString("token:$address",encoded).apply()
    }
    fun token(address: String): String = runCatching {
        val data=Base64.decode(prefs.getString("token:$address",null)?:return "",Base64.NO_WRAP)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,data.copyOfRange(0,12)))
        cipher.doFinal(data.copyOfRange(12,data.size)).toString(Charsets.UTF_8)
    }.getOrDefault("")
    fun forget() { val old=address;prefs.edit().remove("token:$old").remove("address").putBoolean("auto",false).apply() }
}
