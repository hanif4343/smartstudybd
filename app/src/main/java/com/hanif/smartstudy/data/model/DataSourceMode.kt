package com.hanif.smartstudy.data.model

// ─────────────────────────────────────────────────────────────
// Settings → "Data Source" ড্রপডাউন — Quiz/QBank/Study কনটেন্ট (পড়া +
// admin এডিট/আপডেট + সাবজেক্ট তালিকা) কোথা থেকে আসবে তা ঠিক করে।
//
// FIREBASE     → আগের মতোই সরাসরি Firebase Realtime Database (fast, delta-sync সাপোর্ট করে)
// GOOGLE_SHEET → GAS (Google Apps Script) Web App প্রক্সির মাধ্যমে সরাসরি Google Sheet
//                (ধীর হতে পারে, কিন্তু cache-এ একবার এলে পরে ইনস্ট্যান্ট — দেখুন
//                ContentRepository-র cache layer)
//
// একবার সিলেক্ট করলে DataStore-এ (SessionManager) সেভ থাকে, অ্যাপ বন্ধ করে
// আবার খুললেও মনে থাকে।
// ─────────────────────────────────────────────────────────────
enum class DataSourceMode(val storageKey: String, val label: String) {
    FIREBASE("firebase", "Firebase"),
    GOOGLE_SHEET("google_sheet", "Google Sheet");

    companion object {
        fun fromStorageOrDefault(raw: String?): DataSourceMode =
            entries.find { it.storageKey == raw } ?: FIREBASE
    }
}
