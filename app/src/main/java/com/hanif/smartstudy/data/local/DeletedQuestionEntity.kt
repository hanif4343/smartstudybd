package com.hanif.smartstudy.data.local

import androidx.room.Entity

/**
 * ── FIX ("delete korle cache theke instant delete korbe, online a database theke
 * remove hote time lagleo seta never show by anychance" — ডিলিট মানে ডিলিট):
 *
 * আগে ডিলিট করলে cache/Room থেকে সাথে সাথে সরানো হতো ঠিকই (adminDeleteQuestion),
 * কিন্তু Firebase-এ delete টা এখনো "pending" থাকা অবস্থায় যদি কোনো ব্যাকগ্রাউন্ড
 * content-sync চলত (syncToRoom() — নতুন fetch করা পুরো sheet Room-এ upsert করে),
 * আর সার্ভার তখনো পুরনো (এখনো-ডিলিট-না-হওয়া) ডেটা ফেরত দিত, তাহলে সেই "ডিলিট করা"
 * প্রশ্নটাই আবার Room-এ ঢুকে যেত — ইউজার আবার সেটা দেখে ফেলত। এটাই আসল bug।
 *
 * এখন থেকে ডিলিট করার মুহূর্তেই (network এর আগেই) এই ছোট টেবিলে একটা tombstone
 * (সমাধিফলক) রো যোগ হয়। syncToRoom() প্রতিটা upsert-এর আগে এই টেবিল চেক করে —
 * tombstone থাকা কোনো id কখনোই আবার upsert হয় না, সার্ভার যতবারই পুরনো ডেটা
 * ফেরত দিক না কেন। Firebase-এ delete কনফার্ম হলে (immediate বা পরে sync হয়ে)
 * tombstone রো-টা মুছে ফেলা হয় (housekeeping — না মুছলেও ভুল কিছু হয় না, শুধু
 * টেবিলে অল্প কিছু অতিরিক্ত রো থেকে যায়)।
 */
@Entity(
    tableName   = "deleted_questions",
    primaryKeys = ["sheet", "questionId"]
)
data class DeletedQuestionEntity(
    val sheet     : String = "",   // "QUIZ" | "QBANK" | "STUDY" — সবসময় uppercase
    val questionId: String = "",
    val deletedAt : Long    = 0L
)
