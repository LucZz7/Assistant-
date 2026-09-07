package com.example.service

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * JavaScript-to-Native Bridge for WebView / Web App integration.
 * Available in WebView via window.AndroidBridge and window.NecxaBridge.
 */
class AndroidActionBridge(
    private val actionManager: AndroidActionManager,
    private val onActionExecuted: (functionName: String, success: Boolean, details: String) -> Unit = { _, _, _ -> }
) {

    @JavascriptInterface
    fun isAvailable(): Boolean = true

    @JavascriptInterface
    fun openApp(appName: String): String {
        val result = actionManager.openApp(appName)
        onActionExecuted("openApp", result.success, result.message)
        val json = JSONObject().apply {
            put("success", result.success)
            put("message", result.message)
            put("target", result.target)
        }
        return json.toString()
    }

    @JavascriptInterface
    fun makeCall(phoneNumber: String): String {
        val result = actionManager.makeCall(phoneNumber)
        onActionExecuted("makeCall", result.success, result.message)
        val json = JSONObject().apply {
            put("success", result.success)
            put("message", result.message)
            put("target", result.target)
        }
        return json.toString()
    }

    @JavascriptInterface
    fun callContact(contactName: String): String {
        val result = actionManager.callContact(contactName)
        onActionExecuted("callContact", result.success, result.message)
        val json = JSONObject().apply {
            put("success", result.success)
            put("message", result.message)
            put("target", result.target)
            if (result.singleContactMatch != null) {
                put("matchedContact", JSONObject().apply {
                    put("name", result.singleContactMatch.name)
                    put("phoneNumber", result.singleContactMatch.phoneNumber)
                })
            }
            if (result.multipleContactMatches.isNotEmpty()) {
                val array = org.json.JSONArray()
                result.multipleContactMatches.forEach { match ->
                    array.put(JSONObject().apply {
                        put("name", match.name)
                        put("phoneNumber", match.phoneNumber)
                    })
                }
                put("multipleMatches", array)
            }
        }
        return json.toString()
    }

    @JavascriptInterface
    fun openWhatsApp(): String {
        val result = actionManager.openWhatsApp()
        onActionExecuted("openWhatsApp", result.success, result.message)
        val json = JSONObject().apply {
            put("success", result.success)
            put("message", result.message)
            put("target", result.target)
        }
        return json.toString()
    }

    @JavascriptInterface
    fun sendWhatsAppMessage(contactName: String, message: String, phoneNumber: String? = null): String {
        val result = actionManager.sendWhatsAppMessage(contactName, message, phoneNumber)
        onActionExecuted("sendWhatsAppMessage", result.success, result.message)
        val json = JSONObject().apply {
            put("success", result.success)
            put("message", result.message)
            put("target", result.target)
            put("details", result.details)
        }
        return json.toString()
    }

    @JavascriptInterface
    fun openUrl(url: String): String {
        val result = actionManager.openUrl(url)
        onActionExecuted("openUrl", result.success, result.message)
        val json = JSONObject().apply {
            put("success", result.success)
            put("message", result.message)
            put("target", result.target)
        }
        return json.toString()
    }
}
