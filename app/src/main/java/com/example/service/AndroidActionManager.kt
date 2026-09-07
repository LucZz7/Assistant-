package com.example.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.data.model.ActionResult
import com.example.data.model.ActionType
import com.example.data.model.ContactItem

class AndroidActionManager(private val context: Context) {

    // In-memory contact cache and test contacts to assist on fresh emulator environments
    private val testContacts = mutableListOf(
        ContactItem(id = "test_1", name = "Mom", phoneNumber = "+919876543210"),
        ContactItem(id = "test_2", name = "Rahul Sharma", phoneNumber = "+919811122233"),
        ContactItem(id = "test_3", name = "Rahul Verma", phoneNumber = "+919844455566"),
        ContactItem(id = "test_4", name = "Dad", phoneNumber = "+919899988877")
    )

    fun addCustomContact(name: String, number: String) {
        testContacts.add(ContactItem(id = "custom_${System.currentTimeMillis()}", name = name, phoneNumber = number))
    }

    /**
     * Executes opening WhatsApp with deep link and package intent checks
     */
    fun openWhatsApp(): ActionResult {
        val packageManager = context.packageManager
        val packagesToTry = listOf("com.whatsapp", "com.whatsapp.w4b")

        for (pkg in packagesToTry) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return ActionResult(
                        actionType = ActionType.OPEN_WHATSAPP,
                        success = true,
                        message = "Opening WhatsApp...",
                        target = "WhatsApp"
                    )
                }
            } catch (_: Exception) { }
        }

        // Fallback to deep link
        try {
            val deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("whatsapp://send")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (deepLinkIntent.resolveActivity(packageManager) != null) {
                context.startActivity(deepLinkIntent)
                return ActionResult(
                    actionType = ActionType.OPEN_WHATSAPP,
                    success = true,
                    message = "Opening WhatsApp...",
                    target = "WhatsApp"
                )
            }
        } catch (_: Exception) { }

        return ActionResult(
            actionType = ActionType.OPEN_WHATSAPP,
            success = false,
            message = "WhatsApp is not installed on this device.",
            target = "WhatsApp"
        )
    }

    /**
     * Pre-fills a WhatsApp message for a given contact or phone number
     */
    fun sendWhatsAppMessage(contactName: String, message: String, phoneNumber: String? = null): ActionResult {
        var targetPhone = phoneNumber?.replace(Regex("[^0-9+]"), "") ?: ""
        var resolvedName = contactName

        if (targetPhone.isEmpty() && contactName.isNotBlank()) {
            val contactSearch = callContactSearch(contactName)
            if (contactSearch.size == 1) {
                targetPhone = contactSearch.first().phoneNumber.replace(Regex("[^0-9+]"), "")
                resolvedName = contactSearch.first().name
            } else if (contactSearch.size > 1) {
                return ActionResult(
                    actionType = ActionType.SEND_WHATSAPP_MESSAGE,
                    success = false,
                    message = "Found ${contactSearch.size} contacts for '$contactName': ${contactSearch.joinToString(", ") { it.name }}. Please specify which one.",
                    target = contactName,
                    multipleContactMatches = contactSearch
                )
            }
        }

        val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
        val uriString = if (targetPhone.isNotEmpty()) {
            val formatted = if (targetPhone.length == 10 && !targetPhone.startsWith("+")) "91$targetPhone" else targetPhone.removePrefix("+")
            "https://api.whatsapp.com/send?phone=$formatted&text=$encodedMessage"
        } else {
            "https://api.whatsapp.com/send?text=$encodedMessage"
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Fallback without package restriction
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
            ActionResult(
                actionType = ActionType.SEND_WHATSAPP_MESSAGE,
                success = true,
                message = "WhatsApp opened for $resolvedName with your message ready to send.",
                target = resolvedName,
                details = message
            )
        } catch (e: Exception) {
            ActionResult(
                actionType = ActionType.SEND_WHATSAPP_MESSAGE,
                success = false,
                message = "Could not open WhatsApp: ${e.localizedMessage}",
                target = resolvedName
            )
        }
    }

    private fun callContactSearch(contactName: String): List<ContactItem> {
        val trimmedQuery = contactName.trim().lowercase()
        if (trimmedQuery.isEmpty()) return emptyList()

        val aliases = when (trimmedQuery) {
            "mom", "mummy", "mother", "maa" -> listOf("mom", "mummy", "mother", "maa")
            "dad", "papa", "father", "pitaji" -> listOf("dad", "papa", "father", "pitaji")
            else -> listOf(trimmedQuery)
        }

        val matches = mutableListOf<ContactItem>()
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasContactsPermission) {
            try {
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    val idCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    while (it.moveToNext()) {
                        val id = if (idCol >= 0) it.getString(idCol) else ""
                        val name = if (nameCol >= 0) it.getString(nameCol) else ""
                        val number = if (numCol >= 0) it.getString(numCol) else ""

                        if (name.isNotEmpty() && number.isNotEmpty()) {
                            val lowerName = name.lowercase()
                            val isMatch = aliases.any { alias -> lowerName.contains(alias) }
                            if (isMatch && matches.none { existing -> existing.phoneNumber == number }) {
                                matches.add(ContactItem(id = id, name = name, phoneNumber = number))
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        if (matches.isEmpty()) {
            for (contact in testContacts) {
                val lower = contact.name.lowercase()
                if (aliases.any { lower.contains(it) } && matches.none { it.phoneNumber == contact.phoneNumber }) {
                    matches.add(contact)
                }
            }
        }
        return matches
    }

    /**
     * Opens an application by name (e.g. YouTube, Instagram, Chrome, Settings, etc.)
     */
    fun openApp(appName: String): ActionResult {
        val trimmed = appName.trim().lowercase()

        // 1. Check direct aliases
        when {
            trimmed.contains("whatsapp") -> return openWhatsApp()
            trimmed.contains("setting") -> {
                return try {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionResult(ActionType.OPEN_APP, true, "Opening device settings...", target = "Settings")
                } catch (e: Exception) {
                    ActionResult(ActionType.OPEN_APP, false, "Could not open settings: ${e.localizedMessage}", target = "Settings")
                }
            }
            trimmed.contains("youtube") -> {
                val pkg = "com.google.android.youtube"
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                return if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    ActionResult(ActionType.OPEN_APP, true, "Opening YouTube...", target = "YouTube")
                } else {
                    // Fallback to web YouTube
                    openUrl("https://www.youtube.com")
                }
            }
            trimmed.contains("instagram") -> {
                val pkg = "com.instagram.android"
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                return if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    ActionResult(ActionType.OPEN_APP, true, "Opening Instagram...", target = "Instagram")
                } else {
                    openUrl("https://www.instagram.com")
                }
            }
            trimmed.contains("chrome") -> {
                val pkg = "com.android.chrome"
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                return if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    ActionResult(ActionType.OPEN_APP, true, "Opening Chrome...", target = "Chrome")
                } else {
                    openUrl("https://www.google.com")
                }
            }
            trimmed.contains("camera") -> {
                return try {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionResult(ActionType.OPEN_APP, true, "Opening Camera...", target = "Camera")
                } catch (e: Exception) {
                    ActionResult(ActionType.OPEN_APP, false, "Could not open camera: ${e.localizedMessage}", target = "Camera")
                }
            }
            trimmed.contains("map") -> {
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=maps")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionResult(ActionType.OPEN_APP, true, "Opening Maps...", target = "Maps")
                } catch (e: Exception) {
                    openUrl("https://maps.google.com")
                }
            }
        }

        // 2. Search installed applications by label
        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                // Ignore system apps without launcher activity
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                if (label.contains(trimmed) || trimmed.contains(label)) {
                    val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        val actualName = pm.getApplicationLabel(appInfo).toString()
                        return ActionResult(ActionType.OPEN_APP, true, "Opening $actualName...", target = actualName)
                    }
                }
            }
        } catch (_: Exception) { }

        return ActionResult(
            actionType = ActionType.OPEN_APP,
            success = false,
            message = "App '$appName' was not found on this device.",
            target = appName
        )
    }

    /**
     * Opens a web URL in browser
     */
    fun openUrl(url: String): ActionResult {
        val trimmed = url.trim()
        val formatted = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formatted)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(ActionType.OPEN_URL, true, "Opened $formatted", target = formatted)
        } catch (e: Exception) {
            ActionResult(ActionType.OPEN_URL, false, "Failed to open URL: ${e.localizedMessage}", target = formatted)
        }
    }

    /**
     * Initiates phone call or opens dialer
     */
    fun makeCall(phoneNumber: String): ActionResult {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isEmpty()) {
            return ActionResult(ActionType.MAKE_CALL, false, "Invalid phone number provided.", target = phoneNumber)
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            if (hasCallPermission) {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                ActionResult(
                    actionType = ActionType.MAKE_CALL,
                    success = true,
                    message = "Calling $cleanNumber...",
                    target = cleanNumber
                )
            } else {
                // Safe dialer fallback
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                ActionResult(
                    actionType = ActionType.MAKE_CALL,
                    success = true,
                    message = "Opening dialer with $cleanNumber...",
                    target = cleanNumber
                )
            }
        } catch (e: Exception) {
            ActionResult(ActionType.MAKE_CALL, false, "Could not start call: ${e.localizedMessage}", target = cleanNumber)
        }
    }

    /**
     * Searches device contacts and initiates call if exactly 1 match
     */
    fun callContact(contactName: String): ActionResult {
        val trimmedQuery = contactName.trim().lowercase()
        if (trimmedQuery.isEmpty()) {
            return ActionResult(ActionType.CALL_CONTACT, false, "No contact name was specified.", target = contactName)
        }

        // Aliases for family names
        val aliases = when (trimmedQuery) {
            "mom", "mummy", "mother", "maa" -> listOf("mom", "mummy", "mother", "maa")
            "dad", "papa", "father", "pitaji" -> listOf("dad", "papa", "father", "pitaji")
            else -> listOf(trimmedQuery)
        }

        val matches = mutableListOf<ContactItem>()
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        // 1. Search device Contacts Provider if permission granted
        if (hasContactsPermission) {
            try {
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    null
                )

                cursor?.use {
                    val idCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    while (it.moveToNext()) {
                        val id = if (idCol >= 0) it.getString(idCol) else ""
                        val name = if (nameCol >= 0) it.getString(nameCol) else ""
                        val number = if (numCol >= 0) it.getString(numCol) else ""

                        if (name.isNotEmpty() && number.isNotEmpty()) {
                            val lowerName = name.lowercase()
                            val isMatch = aliases.any { alias -> lowerName.contains(alias) }
                            if (isMatch && matches.none { existing -> existing.phoneNumber == number }) {
                                matches.add(ContactItem(id = id, name = name, phoneNumber = number))
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        // 2. If no device matches found (e.g. empty emulator phonebook or permission pending), search companion contacts
        if (matches.isEmpty()) {
            for (contact in testContacts) {
                val lower = contact.name.lowercase()
                if (aliases.any { lower.contains(it) } && matches.none { it.phoneNumber == contact.phoneNumber }) {
                    matches.add(contact)
                }
            }
        }

        return when {
            matches.size == 1 -> {
                val single = matches.first()
                val callResult = makeCall(single.phoneNumber)
                ActionResult(
                    actionType = ActionType.CALL_CONTACT,
                    success = callResult.success,
                    message = "Calling ${single.name} (${single.phoneNumber})...",
                    target = single.name,
                    details = single.phoneNumber,
                    singleContactMatch = single
                )
            }
            matches.size > 1 -> {
                val namesList = matches.joinToString(", ") { it.name }
                ActionResult(
                    actionType = ActionType.CALL_CONTACT,
                    success = false,
                    message = "I found ${matches.size} contacts matching '$contactName': $namesList. Which one should I call?",
                    target = contactName,
                    multipleContactMatches = matches
                )
            }
            else -> {
                ActionResult(
                    actionType = ActionType.CALL_CONTACT,
                    success = false,
                    message = "Contact '$contactName' was not found in your contacts.",
                    target = contactName
                )
            }
        }
    }
}
