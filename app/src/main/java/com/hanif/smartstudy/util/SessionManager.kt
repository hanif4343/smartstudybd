package com.hanif.smartstudy.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.hanif.smartstudy.data.model.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_study_prefs")

class SessionManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        val KEY_USER_JSON  = stringPreferencesKey("ss_user")
        val KEY_DARK_MODE  = booleanPreferencesKey("dark_mode")
        val KEY_FONT_SIZE  = floatPreferencesKey("font_size")
        val KEY_THEME      = stringPreferencesKey("app_theme")
        val KEY_OB_DONE    = booleanPreferencesKey("ob_done")
        val KEY_SOUND_OFF  = booleanPreferencesKey("sound_off")
        val KEY_EXAM_DATE  = stringPreferencesKey("exam_date")
        val KEY_DAILY_GOAL = intPreferencesKey("daily_goal")
        val KEY_USER_NAME  = stringPreferencesKey("home_user_name")
        val KEY_USER_PIC   = stringPreferencesKey("home_user_pic")
    }

    fun isLoggedIn(): Boolean = runBlocking {
        val prefs = context.dataStore.data.first()
        !prefs[KEY_USER_JSON].isNullOrEmpty()
    }

    fun getCurrentUser(): User? = runBlocking {
        try {
            val json = context.dataStore.data.first()[KEY_USER_JSON] ?: return@runBlocking null
            gson.fromJson(json, User::class.java)
        } catch (e: Exception) { null }
    }

    suspend fun saveUser(user: User) {
        context.dataStore.edit { p ->
            p[KEY_USER_JSON] = gson.toJson(user)
            p[KEY_USER_NAME] = user.name ?: ""
            user.picture?.let { p[KEY_USER_PIC] = it }
        }
    }

    suspend fun clearUser() {
        context.dataStore.edit { it.remove(KEY_USER_JSON) }
    }

    fun isDarkMode(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_DARK_MODE] ?: false
    }

    suspend fun setDarkMode(on: Boolean) {
        context.dataStore.edit { it[KEY_DARK_MODE] = on }
    }

    fun isOnboardingDone(): Boolean = runBlocking {
        context.dataStore.data.first()[KEY_OB_DONE] ?: false
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[KEY_OB_DONE] = true }
    }

    fun getDailyGoal(): Int = runBlocking {
        context.dataStore.data.first()[KEY_DAILY_GOAL] ?: 20
    }

    suspend fun setDailyGoal(goal: Int) {
        context.dataStore.edit { it[KEY_DAILY_GOAL] = goal }
    }
}
