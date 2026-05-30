package com.hanif.smartstudy.data.model

data class User(
    val phone      : String? = null,
    val name       : String? = null,
    val password   : String? = null,
    val role       : String? = "User",
    val status     : String? = "Active",
    val picture    : String? = null,
    val userType   : String? = null,
    val classLevel : String? = null,
    val xp         : Int     = 0,
    val fcmToken   : String? = null
) {
    fun isAdmin()    = role?.lowercase() == "admin"
    fun isLoggedIn() = !phone.isNullOrEmpty()
    fun displayName() = if (!name.isNullOrBlank()) name else "ব্যবহারকারী"

    companion object {
        /** Firebase user map থেকে User object বানাও */
        fun fromFirebaseMap(map: Map<String, Any>): User {
            fun get(vararg keys: String) = keys
                .mapNotNull { map[it]?.toString()?.trim()?.ifEmpty { null } }
                .firstOrNull()

            return User(
                phone      = get("Phone", "phone"),
                name       = get("Name", "name"),
                role       = get("Role", "role") ?: "User",
                status     = get("Status", "status") ?: "Active",
                picture    = get("Picture", "picture"),
                userType   = get("UserType", "userType", "Type", "type"),
                classLevel = get("ClassLevel", "classLevel", "Class", "class"),
                xp         = map["XP"]?.toString()?.toIntOrNull()
                          ?: map["xp"]?.toString()?.toIntOrNull() ?: 0
            )
        }
    }
}
