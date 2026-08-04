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
        // ── পূর্ণ কাটওভার — Quiz/QBank/Study এখন Google Sheet-ই primary/একমাত্র সোর্স
        // (RTDB-র সেই node গুলো ডিলিটের পরিকল্পনা করা হচ্ছে বলে), তাই ডিফল্ট এখন
        // GOOGLE_SHEET। ContentFetchService.isGoogleSheetMode() আসলে এই ভ্যালু না পড়ে
        // hardcode true রিটার্ন করে (নিশ্চিত করতে যে পুরনো ডিভাইসে সেভ করা "firebase"
        // সেটিংও content-read কে RTDB-তে ফিরিয়ে নিয়ে যেতে না পারে) — এই ডিফল্ট শুধু
        // ধারাবাহিকতার জন্য, আর যেসব জায়গায় এই raw enum সরাসরি পড়া হয় সেগুলোর জন্য।
        fun fromStorageOrDefault(raw: String?): DataSourceMode =
            entries.find { it.storageKey == raw } ?: GOOGLE_SHEET
    }
}
