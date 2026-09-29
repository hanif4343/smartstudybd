package com.hanif.smartstudy.data.local

import androidx.room.Entity

/**
 * প্রতিটা প্রশ্নের টেকনিক-লিস্ট (অন্য সবার পাবলিক + নিজের — Firebase থেকে merge করা ফলাফল)
 * একবার fetch করার পর এখানে চিরকালের জন্য cache হয়ে থাকে।
 *
 * FIX (Firebase quota): আগে প্রতিবার প্রশ্নে ঢুকলেই (এমনকি টেকনিক-ছাড়া প্রশ্নেও) Firebase-এ
 * read হতো। এখন questionId-র জন্য একটা row এখানে থাকা মানেই "একবার আনা হয়ে গেছে" — যতই
 * প্রশ্নে ফিরে যান, আর Firebase-এ যাওয়া হবে না। row না থাকলে (নতুন প্রশ্ন, বা app
 * ইনস্টল/cache-clear-এর পর প্রথমবার) শুধু তখনই একবার fetch হবে — দেখো ui/shared/
 * SharedComponents.kt-এর UserTechniqueSection.loadAll()।
 *
 * json = সেই মুহূর্তের পুরো merge-করা টেকনিক-লিস্ট (Gson দিয়ে serialize করা List<UserTechnique>),
 * খালি লিস্ট হলেও "[]" হিসেবে সেভ থাকে — তাই "কখনো fetch করা হয়নি" আর "fetch করে খালি
 * পাওয়া গেছে" এই দুইটার মধ্যে পার্থক্য বোঝা যায় (row থাকা/না-থাকা দিয়েই)।
 */
@Entity(tableName = "technique_cache", primaryKeys = ["questionId"])
data class TechniqueCacheEntity(
    val questionId : String = "",
    val json       : String = "[]",
    val fetchedAt  : Long   = 0L
)
