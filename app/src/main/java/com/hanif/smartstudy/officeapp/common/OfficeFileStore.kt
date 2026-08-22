package com.hanif.smartstudy.officeapp.common

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.util.UUID

/**
 * ══════════════════════════════════════════════════════════════════
 *  Office App — কোন module (Word/Excel/PowerPoint) খোলা তা ট্র্যাক করার enum।
 *  MainScreen.kt-এ officeAppModule state এই enum ব্যবহার করবে।
 * ══════════════════════════════════════════════════════════════════
 */
enum class OfficeModule { WORD, EXCEL, PPT }

/**
 * একটা সেভ করা Office ফাইলের মেটাডেটা + কনটেন্ট।
 * Word: content = editor.innerHTML
 * Excel/PPT (ভবিষ্যতে): content = JSON string (grid/slide state)
 */
data class OfficeFile(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("module") val module: OfficeModule,
    @SerializedName("title") var title: String,
    @SerializedName("content") var content: String = "",
    @SerializedName("pageMarginTop") var pageMarginTop: Int = 25,
    @SerializedName("pageMarginRight") var pageMarginRight: Int = 20,
    @SerializedName("pageMarginBottom") var pageMarginBottom: Int = 25,
    @SerializedName("pageMarginLeft") var pageMarginLeft: Int = 25,
    @SerializedName("pageSize") var pageSize: String = "a4", // a4 | legal | a5
    @SerializedName("pageBorder") var pageBorder: Boolean = false,
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("updatedAt") var updatedAt: Long = System.currentTimeMillis()
)

/**
 * ══════════════════════════════════════════════════════════════════
 *  OfficeFileStore — Room এর বদলে হালকা local-file (JSON) based storage।
 *  app-এর filesDir/officeapp/<module>/<id>.json এ প্রতিটা ডকুমেন্ট সেভ হয়।
 *  Phase পরবর্তীতে দরকার হলে Room-এ migrate করা সহজ, কারণ সব I/O এই এক
 *  ক্লাসেই বন্দি।
 * ══════════════════════════════════════════════════════════════════
 */
class OfficeFileStore(private val context: Context) {
    private val gson = Gson()

    private fun dirFor(module: OfficeModule): File {
        val dir = File(context.filesDir, "officeapp/${module.name.lowercase()}")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun list(module: OfficeModule): List<OfficeFile> {
        val dir = dirFor(module)
        val files = dir.listFiles { f -> f.extension == "json" } ?: emptyArray()
        return files.mapNotNull { f ->
            try {
                gson.fromJson(f.readText(), OfficeFile::class.java)
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.updatedAt }
    }

    fun load(module: OfficeModule, id: String): OfficeFile? {
        val f = File(dirFor(module), "$id.json")
        if (!f.exists()) return null
        return try {
            gson.fromJson(f.readText(), OfficeFile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun save(file: OfficeFile): OfficeFile {
        file.updatedAt = System.currentTimeMillis()
        val f = File(dirFor(file.module), "${file.id}.json")
        f.writeText(gson.toJson(file))
        return file
    }

    fun delete(module: OfficeModule, id: String) {
        File(dirFor(module), "$id.json").delete()
    }

    fun createNew(module: OfficeModule, title: String, initialContent: String = ""): OfficeFile {
        val file = OfficeFile(module = module, title = title, content = initialContent)
        return save(file)
    }
}
