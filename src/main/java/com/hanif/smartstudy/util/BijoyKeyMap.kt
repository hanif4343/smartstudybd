package com.hanif.smartstudy.util

/**
 * বাংলা ক্যারেক্টার → বিজয় কীবোর্ড লেআউটে ফিজিক্যাল কী (+ Shift লাগে কিনা) —
 * ইউজারের নিজের Bijoymaper.java (কী → ইউনিকোড ম্যাপ)-এর ঠিক উল্টো দিক থেকে বানানো।
 * Live next-key হাইলাইট কীবোর্ডে ব্যবহার হয় (দেখো
 * ui/typing/FingerKeyboardDiagram.kt-এর LiveKeyHighlightKeyboard) — এখন কোন
 * ক্যারেক্টার টাইপ করার কথা সেই ফিজিক্যাল কী-টা হাইলাইট করতে এখান থেকেই লুকআপ হয়।
 *
 * নোট: Bijoymaper.java-তে যেসব এন্ট্রি multi-codepoint আউটপুট দেয় (যেমন
 * Shift+A → "র্" রেফ, বা z/Shift+Z → "্র"/"্য" ফলা) সেগুলো ইচ্ছাকৃতভাবে বাদ —
 * পাসেজের টেক্সটে ওগুলো এমনিতেই আলাদা আলাদা একক ক্যারেক্টার হিসেবেই থাকে
 * (হসন্ত + র/য), যেগুলো নিজেদের একক এন্ট্রি (্ → G, র → V, য → W) দিয়েই
 * ঠিকভাবে কভার হয়ে যায় — char-বাই-char হাইলাইটের জন্য আলাদা এন্ট্রি লাগে না।
 *
 * ডুপ্লিকেট আউটপুট (একই আউটপুট একাধিক কী থেকে) থাকলে প্রথম/প্রধান কী-টাই
 * রাখা হয়েছে (যেমন ৎ → "9"+Shift, ঃ → "0"+Shift — এগুলো ডিজিট-রো-তেই আছে,
 * তাই আলাদা "\\" কী-এর দরকার পড়েনি, যেটা আমাদের ফিজিক্যাল রো-লেআউটে নেই)।
 */
data class BijoyKey(val label: String, val shift: Boolean)

object BijoyKeyMap {

    private val MAP: Map<Char, BijoyKey> = mapOf(
        // ── স্বরবর্ণ ও কার চিহ্ন ──
        '\u09BE' to BijoyKey("F", false),   // া
        '\u0985' to BijoyKey("F", true),    // অ
        '\u09BF' to BijoyKey("D", false),   // ি
        '\u09C0' to BijoyKey("D", true),    // ী
        '\u09C1' to BijoyKey("S", false),   // ু
        '\u09C2' to BijoyKey("S", true),    // ূ
        '\u09C3' to BijoyKey("A", false),   // ৃ
        '\u09C7' to BijoyKey("C", false),   // ে
        '\u09C8' to BijoyKey("C", true),    // ৈ
        '\u0993' to BijoyKey("X", false),   // ও
        '\u09CC' to BijoyKey("X", true),    // ৌ

        // ── ব্যঞ্জনবর্ণ ──
        '\u0995' to BijoyKey("J", false),   // ক
        '\u0996' to BijoyKey("J", true),    // খ
        '\u0997' to BijoyKey("O", false),   // গ
        '\u0998' to BijoyKey("O", true),    // ঘ
        '\u0999' to BijoyKey("Q", false),   // ঙ
        '\u0982' to BijoyKey("Q", true),    // ং

        '\u099A' to BijoyKey("Y", false),   // চ
        '\u099B' to BijoyKey("Y", true),    // ছ
        '\u099C' to BijoyKey("U", false),   // জ
        '\u099D' to BijoyKey("U", true),    // ঝ
        '\u09B9' to BijoyKey("I", false),   // হ
        '\u099E' to BijoyKey("I", true),    // ঞ

        '\u099F' to BijoyKey("T", false),   // ট
        '\u09A0' to BijoyKey("T", true),    // ঠ
        '\u09A1' to BijoyKey("E", false),   // ড
        '\u09A2' to BijoyKey("E", true),    // ঢ
        '\u09A8' to BijoyKey("B", false),   // ন
        '\u09A3' to BijoyKey("B", true),    // ণ

        '\u09A4' to BijoyKey("K", false),   // ত
        '\u09A5' to BijoyKey("K", true),    // থ
        '\u09A6' to BijoyKey("L", false),   // দ
        '\u09A7' to BijoyKey("L", true),    // ধ

        '\u09AA' to BijoyKey("R", false),   // প
        '\u09AB' to BijoyKey("R", true),    // ফ
        '\u09AC' to BijoyKey("H", false),   // ব
        '\u09AD' to BijoyKey("H", true),    // ভ
        '\u09AE' to BijoyKey("M", false),   // ম

        '\u09DC' to BijoyKey("P", false),   // ড়
        '\u09DD' to BijoyKey("P", true),    // ঢ়
        '\u09B0' to BijoyKey("V", false),   // র
        '\u09B2' to BijoyKey("V", true),    // ল

        '\u09B8' to BijoyKey("N", false),   // স
        '\u09B7' to BijoyKey("N", true),    // ষ
        '\u09B6' to BijoyKey("M", true),    // শ

        '\u09AF' to BijoyKey("W", false),   // য
        '\u09DF' to BijoyKey("W", true),    // য়

        // ── হসন্ত/দাঁড়ি/বিশেষ চিহ্ন ──
        '\u09CD' to BijoyKey("G", false),   // ্ (হসন্ত)
        '\u0964' to BijoyKey("G", true),    // । (দাঁড়ি)
        '\u09CE' to BijoyKey("9", true),    // ৎ (খণ্ড-ত) — Shift+9
        '\u0983' to BijoyKey("0", true),    // ঃ (বিসর্গ) — Shift+0
        '\u0981' to BijoyKey("7", true),    // ঁ (চন্দ্রবিন্দু) — Shift+7

        // ── বাংলা সংখ্যা ──
        '\u09E7' to BijoyKey("1", false), '\u09E8' to BijoyKey("2", false),
        '\u09E9' to BijoyKey("3", false), '\u09EA' to BijoyKey("4", false),
        '\u09EB' to BijoyKey("5", false), '\u09EC' to BijoyKey("6", false),
        '\u09ED' to BijoyKey("7", false), '\u09EE' to BijoyKey("8", false),
        '\u09EF' to BijoyKey("9", false), '\u09E6' to BijoyKey("0", false)
    )

    /** টার্গেট বাংলা ক্যারেক্টার থেকে ফিজিক্যাল কী + Shift লাগবে কিনা — ম্যাপে
     *  না থাকলে null (তখন লাইভ কীবোর্ড কোনো কী হাইলাইট করবে না, ক্র্যাশ করবে না)। */
    fun keyFor(ch: Char): BijoyKey? = MAP[ch]
}
