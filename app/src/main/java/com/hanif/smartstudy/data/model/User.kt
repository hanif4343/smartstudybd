package com.hanif.smartstudy.data.model

data class User(
    val phone     : String? = null,
    val name      : String? = null,
    val password  : String? = null,
    val role      : String? = "User",
    val status    : String? = null,
    val picture   : String? = null,
    val userType  : String? = null,
    val classLevel: String? = null,
    val xp        : Int     = 0,
    val fcmToken  : String? = null
) {
    fun isAdmin()    = role?.lowercase() == "admin"
    fun isLoggedIn() = !phone.isNullOrEmpty()
    fun displayName() = name ?: "ব্যবহারকারী"
}
