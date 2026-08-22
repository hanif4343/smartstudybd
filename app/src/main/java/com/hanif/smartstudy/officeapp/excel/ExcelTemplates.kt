package com.hanif.smartstudy.officeapp.excel

/**
 * ══════════════════════════════════════════════════════════════════
 *  ExcelTemplates — সরকারি চাকরি পরীক্ষায় দরকারি ready-made spreadsheet
 *  টেমপ্লেট। প্রতিটা "cells" ম্যাপ raw cell value/formula ধারণ করে
 *  (ref -> raw string, যেমন "=B2*0.1"), যা সরাসরি JS sheet-state এ বসে।
 * ══════════════════════════════════════════════════════════════════
 */
data class ExcelTemplate(
    val id: String,
    val title: String,
    val emoji: String,
    val numCols: Int,
    val numRows: Int,
    val cells: Map<String, String>
)

object ExcelTemplates {

    val BLANK = ExcelTemplate("blank", "খালি শীট", "📄", 12, 30, emptyMap())

    /** বেতন শীট — Tax ১০%, Bonus ৫%, Net Pay = Basic - Tax + Bonus */
    val SALARY_SHEET = ExcelTemplate(
        id = "salary", title = "বেতন শীট (Salary + Tax + Bonus)", emoji = "💰",
        numCols = 6, numRows = 12,
        cells = mapOf(
            "A1" to "ক্রমিক", "B1" to "নাম", "C1" to "মূল বেতন", "D1" to "কর (১০%)", "E1" to "বোনাস (৫%)", "F1" to "নীট বেতন",
            "A2" to "1", "B2" to "কর্মচারী ১", "C2" to "25000", "D2" to "=C2*0.1", "E2" to "=C2*0.05", "F2" to "=C2-D2+E2",
            "A3" to "2", "B3" to "কর্মচারী ২", "C3" to "30000", "D3" to "=C3*0.1", "E3" to "=C3*0.05", "F3" to "=C3-D3+E3",
            "A4" to "3", "B4" to "কর্মচারী ৩", "C4" to "22000", "D4" to "=C4*0.1", "E4" to "=C4*0.05", "F4" to "=C4-D4+E4",
            "B6" to "মোট:", "F6" to "=SUM(F2:F4)"
        )
    )

    /** ছাত্র ফলাফল শীট — Fail Priority Logic: যেকোনো একটি বিষয়ে ৩৩-এর কম হলেই সামগ্রিক ফলাফল Fail */
    val RESULT_SHEET = ExcelTemplate(
        id = "gpa", title = "ছাত্র ফলাফল শীট (GPA + Fail Priority)", emoji = "🎓",
        numCols = 7, numRows = 12,
        cells = mapOf(
            "A1" to "ক্রমিক", "B1" to "নাম", "C1" to "বাংলা", "D1" to "ইংরেজি", "E1" to "গণিত", "F1" to "মোট", "G1" to "ফলাফল",
            "A2" to "1", "B2" to "ছাত্র ১", "C2" to "45", "D2" to "38", "E2" to "50",
            "F2" to "=SUM(C2:E2)",
            "G2" to "=IF(AND(C2>=33,D2>=33,E2>=33),\"Pass\",\"Fail\")",
            "A3" to "2", "B3" to "ছাত্র ২", "C3" to "28", "D3" to "60", "E3" to "55",
            "F3" to "=SUM(C3:E3)",
            "G3" to "=IF(AND(C3>=33,D3>=33,E3>=33),\"Pass\",\"Fail\")",
            "A4" to "3", "B4" to "ছাত্র ৩", "C4" to "40", "D4" to "42", "E4" to "38",
            "F4" to "=SUM(C4:E4)",
            "G4" to "=IF(AND(C4>=33,D4>=33,E4>=33),\"Pass\",\"Fail\")",
            "B6" to "মোট Pass সংখ্যা:", "C6" to "=COUNTIF(G2:G4,\"Pass\")"
        )
    )

    /** ইনভেন্টরি লেজার — Closing Stock = Opening + Purchase - Sold */
    val INVENTORY = ExcelTemplate(
        id = "inventory", title = "ইনভেন্টরি লেজার (Inventory Ledger)", emoji = "📦",
        numCols = 5, numRows = 12,
        cells = mapOf(
            "A1" to "পণ্যের নাম", "B1" to "প্রারম্ভিক মজুদ", "C1" to "ক্রয়", "D1" to "বিক্রয়", "E1" to "সমাপনী মজুদ",
            "A2" to "পণ্য ১", "B2" to "100", "C2" to "50", "D2" to "80", "E2" to "=B2+C2-D2",
            "A3" to "পণ্য ২", "B3" to "200", "C3" to "0", "D3" to "60", "E3" to "=B3+C3-D3",
            "A4" to "পণ্য ৩", "B4" to "75", "C4" to "25", "D4" to "40", "E4" to "=B4+C4-D4"
        )
    )

    /** বিল/ভাউচার — Amount = Qty × Rate, Total = SUM */
    val BILL_VOUCHER = ExcelTemplate(
        id = "bill", title = "বিল / ভাউচার (Bill & Voucher)", emoji = "🧾",
        numCols = 4, numRows = 12,
        cells = mapOf(
            "A1" to "পণ্য/সেবা", "B1" to "পরিমাণ", "C1" to "একক মূল্য", "D1" to "মোট",
            "A2" to "আইটেম ১", "B2" to "2", "C2" to "150", "D2" to "=B2*C2",
            "A3" to "আইটেম ২", "B3" to "1", "C3" to "500", "D3" to "=B3*C3",
            "A4" to "আইটেম ৩", "B4" to "3", "C4" to "80", "D4" to "=B4*C4",
            "A6" to "সর্বমোট:", "D6" to "=SUM(D2:D4)"
        )
    )

    val all = listOf(BLANK, SALARY_SHEET, RESULT_SHEET, INVENTORY, BILL_VOUCHER)
}
