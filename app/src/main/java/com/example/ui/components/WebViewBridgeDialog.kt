package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.service.AndroidActionBridge

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewBridgeDialog(
    actionBridge: AndroidActionBridge,
    onDismiss: () -> Unit
) {
    val bridgeHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    background-color: #0d1117;
                    color: #e6edf3;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    padding: 16px;
                    margin: 0;
                }
                h3 { color: #58a6ff; margin-top: 0; }
                .status-box {
                    background: #161b22;
                    border: 1px solid #30363d;
                    padding: 12px;
                    border-radius: 8px;
                    margin-bottom: 16px;
                    font-size: 13px;
                }
                .badge {
                    display: inline-block;
                    padding: 3px 8px;
                    border-radius: 12px;
                    font-size: 11px;
                    font-weight: bold;
                    background: #238636;
                    color: white;
                }
                .btn {
                    display: block;
                    width: 100%;
                    padding: 10px;
                    margin-bottom: 8px;
                    background: #21262d;
                    border: 1px solid #30363d;
                    color: #58a6ff;
                    border-radius: 6px;
                    font-weight: 600;
                    text-align: left;
                    font-size: 13px;
                }
                .btn:active { background: #30363d; }
                #log {
                    background: #000000;
                    padding: 8px;
                    border-radius: 6px;
                    color: #7ee787;
                    font-family: monospace;
                    font-size: 12px;
                    max-height: 120px;
                    overflow-y: auto;
                    margin-top: 12px;
                    white-space: pre-wrap;
                }
            </style>
        </head>
        <body>
            <h3>Android Native Action Bridge</h3>
            <div class="status-box">
                Bridge Status: <span id="bridgeStatus" class="badge">Checking...</span><br>
                <small style="color:#8b949e;">window.AndroidBridge & window.NecxaBridge exposed to JS</small>
            </div>

            <button class="btn" onclick="testWhatsApp()">🟢 openWhatsApp()</button>
            <button class="btn" onclick="testSendWhatsAppMessage()">💬 sendWhatsAppMessage("Rahul", "Hello")</button>
            <button class="btn" onclick="testCallContact()">📞 callContact("Mom")</button>
            <button class="btn" onclick="testCallMultiple()">👥 callContact("Rahul")</button>
            <button class="btn" onclick="testMakeCall()">📱 makeCall("9876543210")</button>
            <button class="btn" onclick="testOpenApp()">🎬 openApp("YouTube")</button>
            <button class="btn" onclick="testOpenUrl()">🌐 openUrl("https://ai.google.dev")</button>

            <div id="log">Console ready. Tap any button above to test bridge.</div>

            <script>
                function log(msg) {
                    const l = document.getElementById('log');
                    l.innerText = msg;
                }

                function getBridge() {
                    return window.AndroidBridge || window.NecxaBridge;
                }

                window.onload = function() {
                    const bridge = getBridge();
                    const status = document.getElementById('bridgeStatus');
                    if (bridge && typeof bridge.openWhatsApp === 'function') {
                        status.innerText = "ONLINE (Native APK Bridge)";
                        status.style.background = "#238636";
                    } else {
                        status.innerText = "OFFLINE (Browser Fallback)";
                        status.style.background = "#d29922";
                    }
                };

                function testWhatsApp() {
                    const bridge = getBridge();
                    if (bridge) {
                        const res = bridge.openWhatsApp();
                        log("openWhatsApp() => " + res);
                    } else {
                        log("Fallback: window.location.href = 'whatsapp://send'");
                        window.location.href = "whatsapp://send";
                    }
                }

                function testSendWhatsAppMessage() {
                    const bridge = getBridge();
                    if (bridge && typeof bridge.sendWhatsAppMessage === 'function') {
                        const res = bridge.sendWhatsAppMessage("Rahul", "Hello");
                        log("sendWhatsAppMessage('Rahul', 'Hello') => " + res);
                    } else {
                        log("Fallback: window.location.href = 'https://api.whatsapp.com/send?text=Hello'");
                        window.location.href = "https://api.whatsapp.com/send?text=Hello";
                    }
                }

                function testCallContact() {
                    const bridge = getBridge();
                    if (bridge) {
                        const res = bridge.callContact("Mom");
                        log("callContact('Mom') => " + res);
                    } else {
                        log("Error: Contacts API unavailable in normal browser");
                    }
                }

                function testCallMultiple() {
                    const bridge = getBridge();
                    if (bridge) {
                        const res = bridge.callContact("Rahul");
                        log("callContact('Rahul') => " + res);
                    }
                }

                function testMakeCall() {
                    const bridge = getBridge();
                    if (bridge) {
                        const res = bridge.makeCall("9876543210");
                        log("makeCall('9876543210') => " + res);
                    } else {
                        window.location.href = "tel:9876543210";
                    }
                }

                function testOpenApp() {
                    const bridge = getBridge();
                    if (bridge) {
                        const res = bridge.openApp("YouTube");
                        log("openApp('YouTube') => " + res);
                    }
                }

                function testOpenUrl() {
                    const bridge = getBridge();
                    if (bridge) {
                        const res = bridge.openUrl("https://ai.google.dev");
                        log("openUrl(...) => " + res);
                    } else {
                        window.open("https://ai.google.dev", "_blank");
                    }
                }
            </script>
        </body>
        </html>
    """.trimIndent()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .testTag("webview_bridge_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B22))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Bridge",
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "JS-to-Native Bridge Inspector",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            webViewClient = WebViewClient()

                            // Expose bridge both as AndroidBridge and NecxaBridge
                            addJavascriptInterface(actionBridge, "AndroidBridge")
                            addJavascriptInterface(actionBridge, "NecxaBridge")

                            loadDataWithBaseURL("https://necxa.local", bridgeHtml, "text/html", "UTF-8", null)
                        }
                    }
                )
            }
        }
    }
}
