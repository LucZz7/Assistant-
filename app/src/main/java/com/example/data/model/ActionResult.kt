package com.example.data.model

data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val type: String = "Mobile"
)

data class ActionResult(
    val actionType: ActionType,
    val success: Boolean,
    val message: String,
    val target: String = "",
    val details: String = "",
    val singleContactMatch: ContactItem? = null,
    val multipleContactMatches: List<ContactItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class ActionType {
    OPEN_WHATSAPP,
    SEND_WHATSAPP_MESSAGE,
    OPEN_APP,
    OPEN_URL,
    MAKE_CALL,
    CALL_CONTACT,
    LANGUAGE_SWITCH,
    GENERAL_QUERY
}
