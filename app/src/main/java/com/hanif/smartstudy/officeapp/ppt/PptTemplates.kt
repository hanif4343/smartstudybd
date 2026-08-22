package com.hanif.smartstudy.officeapp.ppt

/**
 * ══════════════════════════════════════════════════════════════════
 *  PptTemplates — সরকারি চাকরির ইন্টারভিউ/উপস্থাপনার জন্য রেডি স্লাইড
 *  টেমপ্লেট। প্রতিটা slide raw Map হিসেবে সংজ্ঞায়িত, যা presentation
 *  state JSON-এ রূপান্তরিত হয়ে window.setPresentationState() এ বসে।
 * ══════════════════════════════════════════════════════════════════
 */
data class PptSlideDef(
    val bg: String,
    val elements: List<Map<String, Any>>
)

data class PptTemplate(val id: String, val title: String, val emoji: String, val slides: List<PptSlideDef>)

object PptTemplates {

    val BLANK = PptTemplate(
        id = "blank", title = "খালি প্রেজেন্টেশন", emoji = "📄",
        slides = listOf(PptSlideDef(bg = "#FFFFFF", elements = emptyList()))
    )

    private fun textEl(x: Int, y: Int, w: Int, h: Int, text: String, size: Int, color: String, bold: Boolean = false) =
        mapOf(
            "id" to "el${(0..999999).random()}", "type" to "text",
            "x" to x, "y" to y, "w" to w, "h" to h,
            "text" to text, "fontSize" to size, "color" to color, "bold" to bold
        )

    private fun rectEl(x: Int, y: Int, w: Int, h: Int, color: String) =
        mapOf("id" to "el${(0..999999).random()}", "type" to "rect", "x" to x, "y" to y, "w" to w, "h" to h, "color" to color)

    /** চাকরির ইন্টারভিউ / সেমিনার উপস্থাপনার নমুনা — ৫টি স্লাইড */
    val JOB_INTERVIEW = PptTemplate(
        id = "job_interview", title = "চাকরির ইন্টারভিউ প্রেজেন্টেশন", emoji = "💼",
        slides = listOf(
            // Slide 1: Title
            PptSlideDef(
                bg = "linear-gradient(135deg,#2563EB,#7C3AED)",
                elements = listOf(
                    textEl(80, 180, 800, 100, "আত্মপরিচিতি", 64, "#FFFFFF", bold = true),
                    textEl(80, 290, 800, 60, "নাম | পদবি | প্রতিষ্ঠান", 28, "#E0E7FF")
                )
            ),
            // Slide 2: About Me
            PptSlideDef(
                bg = "#FFFFFF",
                elements = listOf(
                    rectEl(0, 0, 960, 90, "#2563EB"),
                    textEl(40, 20, 600, 50, "আমার সম্পর্কে", 36, "#FFFFFF", bold = true),
                    textEl(60, 130, 820, 350, "• শিক্ষাগত যোগ্যতা: ..........................\n• অভিজ্ঞতা: ..........................\n• দক্ষতা: ..........................\n• অর্জন: ..........................", 26, "#1F2937")
                )
            ),
            // Slide 3: Career Objective
            PptSlideDef(
                bg = "#F8FAFC",
                elements = listOf(
                    rectEl(0, 0, 960, 90, "#16A34A"),
                    textEl(40, 20, 600, 50, "ক্যারিয়ার লক্ষ্য", 36, "#FFFFFF", bold = true),
                    textEl(60, 150, 820, 300, "আমার লক্ষ্য হলো ..................................................................\n..................................................................", 26, "#1F2937")
                )
            ),
            // Slide 4: Why Should We Hire You
            PptSlideDef(
                bg = "#FFFFFF",
                elements = listOf(
                    rectEl(0, 0, 960, 90, "#EA580C"),
                    textEl(40, 20, 700, 50, "কেন আমাকে নিয়োগ দেবেন?", 32, "#FFFFFF", bold = true),
                    textEl(60, 130, 820, 350, "• দক্ষতা ও অভিজ্ঞতা\n• প্রতিষ্ঠানের প্রতি অঙ্গীকার\n• দলগত কাজে পারদর্শিতা\n• সমস্যা সমাধানের ক্ষমতা", 26, "#1F2937")
                )
            ),
            // Slide 5: Thank You
            PptSlideDef(
                bg = "linear-gradient(135deg,#EA580C,#DC2626)",
                elements = listOf(
                    textEl(200, 220, 560, 100, "ধন্যবাদ", 64, "#FFFFFF", bold = true),
                    textEl(200, 320, 560, 50, "প্রশ্নের জন্য আমি প্রস্তুত", 26, "#FFE4D6")
                )
            )
        )
    )

    val all = listOf(BLANK, JOB_INTERVIEW)
}
