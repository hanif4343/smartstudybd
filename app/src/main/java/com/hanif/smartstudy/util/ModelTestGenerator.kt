package com.hanif.smartstudy.util

import com.hanif.smartstudy.data.model.QuestionItem
import kotlin.random.Random

/**
 * ModelTestGenerator — Admin bulk-generate অ্যালগরিদম।
 *
 * ইনপুট: একটা subject-এর সব প্রশ্ন (Quiz + QBank মিলিয়ে pool করা)।
 * আউটপুট: N টা Model Test, প্রতিটায় M টা প্রশ্ন —
 *   - প্রতিটায় "গুরুত্বপূর্ণ" (isImportant) প্রশ্ন থেকে ~৩০-৪০%,
 *   - বাকিটা normal pool থেকে, আগের টেস্টগুলোয় কম ব্যবহৃত প্রশ্নকে প্রাধান্য দিয়ে।
 *   ফলে টেস্ট ১, ২, ৩...-এ কিছু প্রশ্ন কমন থাকবে (গুরুত্বপূর্ণগুলো বারবার আসবে),
 *   আবার কিছু নতুন থাকবে (normal pool ঘুরে ঘুরে কভার হবে)।
 *
 * প্রশ্ন কম থাকলে (pool < perTest) একটা টেস্টে একই প্রশ্ন দুইবার বসানো হয় না —
 * বরং সেই টেস্টে যতগুলো ইউনিক প্রশ্ন পাওয়া যায় ততগুলোই থাকে, আর warning ফেরত দেওয়া হয়।
 *
 * ── Phase 6 আপডেট — group_id-aware selection: multi-part প্রশ্ন (একই instruction-এর
 * কয়েকটা sub-question, যেমন "কারক নির্ণয় কর", BulkUploaderPage-এর 🔗 Group Mode দিয়ে
 * Admin App-এ যোগ করা) এখন QuestionItem.groupId দিয়ে চিহ্নিত থাকে। এই জেনারেটর প্রতিটা
 * groupId-কে একটা অবিভাজ্য "unit" হিসেবে ট্রিট করে — হয় গ্রুপের সবগুলো sub-question একসাথে
 * সিলেক্ট হবে, নয়তো একটাও না। group_id ছাড়া প্রশ্ন আগের মতোই একক (size-1) unit। এটা
 * Admin App-এর src/core/modelTestGenerator.js (JS ভার্সন, Phase 5) এর হুবহু Kotlin পোর্ট —
 * দুই জায়গাতেই একই অ্যালগরিদম যাতে ওয়েব আর Android অ্যাপ একই রকম টেস্ট বানায়।
 */
object ModelTestGenerator {

    data class GeneratedTest(
        val testNumber   : Int,
        val questionKeys : List<String>   // QuestionItem.sourceKey() — "sheet|id"
    )

    data class GenerateResult(
        val tests   : List<GeneratedTest>,
        val warning : String? = null
    )

    // group_id-aware selection-এর একক — একই groupId-এর সব sourceKey একসাথে একটা
    // অবিভাজ্য ইউনিট, groupId ছাড়া প্রশ্ন নিজেই একটা ইউনিট (size 1)। নাম "SelectionUnit"
    // (শুধু "Unit" না) — kotlin.Unit-এর সাথে বিভ্রান্তি এড়াতে।
    private data class SelectionUnit(
        val unitKey    : String,
        val groupId    : String,      // "" মানে standalone প্রশ্ন (group নেই)
        val sourceKeys : List<String>,
        val important  : Boolean
    ) {
        val size get() = sourceKeys.size
    }

    // pool-এর distinct sourceKey থেকে groupId অনুযায়ী unit বানায়। একই group-এর
    // sub-question গুলো subIndex অনুযায়ী সাজানো থাকে (টেস্টে পাশাপাশি সঠিক ক্রমে
    // দেখানোর জন্য)। ইনসার্শন-অর্ডার সংরক্ষিত (LinkedHashMap) — ফলাফল deterministic-ish।
    private fun buildUnits(pool: List<QuestionItem>): List<SelectionUnit> {
        val seen = HashSet<String>()
        val groupMap = LinkedHashMap<String, MutableList<QuestionItem>>()
        val singles = mutableListOf<QuestionItem>()

        pool.forEach { q ->
            val key = q.sourceKey()
            if (!seen.add(key)) return@forEach
            if (q.isGrouped()) {
                groupMap.getOrPut(q.groupId) { mutableListOf() }.add(q)
            } else {
                singles.add(q)
            }
        }

        val units = mutableListOf<SelectionUnit>()
        groupMap.forEach { (gid, items) ->
            val sorted = items.sortedBy { it.subIndex }
            units.add(
                SelectionUnit(
                    unitKey    = "G|$gid",
                    groupId    = gid,
                    sourceKeys = sorted.map { it.sourceKey() },
                    important  = sorted.any { it.isImportant }
                )
            )
        }
        singles.forEach { q ->
            units.add(SelectionUnit(unitKey = q.sourceKey(), groupId = "", sourceKeys = listOf(q.sourceKey()), important = q.isImportant))
        }
        return units
    }

    fun generate(
        pool                : List<QuestionItem>,
        count               : Int,
        perTest             : Int,
        importantRatioRange : ClosedFloatingPointRange<Double> = 0.30..0.40,
        seed                : Long? = null
    ): GenerateResult {
        if (pool.isEmpty() || count <= 0 || perTest <= 0) {
            return GenerateResult(emptyList(), "❌ প্রশ্ন পুল খালি অথবা সংখ্যা ভুল — Model Test বানানো যায়নি")
        }

        val rnd = if (seed != null) Random(seed) else Random.Default
        val units = buildUnits(pool)
        val unitMap = units.associateBy { it.unitKey }
        val totalQuestions = units.sumOf { it.size }
        val importantUnits = units.filter { it.important }
        val oversizedGroups = units.filter { it.groupId.isNotBlank() && it.size > perTest }

        val warnings = mutableListOf<String>()
        if (totalQuestions < perTest) {
            warnings += "⚠️ এই subject-এ মোট ${totalQuestions}টি প্রশ্ন আছে (গ্রুপসহ), কিন্তু প্রতি টেস্টে $perTest টি চাওয়া হয়েছে — " +
                "প্রতিটা টেস্টে যতগুলো সম্ভব ততগুলোই থাকবে (repeat বাধ্যতামূলক আলাদা টেস্টগুলোর মধ্যে)"
        }
        if (oversizedGroups.isNotEmpty()) {
            warnings += "⚠️ ${oversizedGroups.size}টি multi-part প্রশ্ন-গ্রুপ (sub-question সংখ্যা প্রতি টেস্টের " +
                "${perTest}-এর চেয়ে বেশি) কোনো টেস্টেই ঢুকতে পারবে না — group ভাঙা হয় না বলে"
        }
        val warning = if (warnings.isNotEmpty()) warnings.joinToString(" | ") else null

        // usage[unitKey] = কতবার আগের generated টেস্টগুলোতে এসেছে — কম ব্যবহৃতকে প্রাধান্য দিতে
        val usage = HashMap<String, Int>().apply { units.forEach { put(it.unitKey, 0) } }

        // candidates থেকে unit বাছাই করে `used`-এ যোগ করে, যতক্ষণ না remaining বাজেট শেষ হয়।
        // কোনো unit-এর size বাজেটের চেয়ে বড় হলে এই রাউন্ডে স্কিপ (group কখনো ভাঙা হয় না),
        // কিন্তু ছোট unit দিয়ে বাজেট পূরণের চেষ্টা চলতেই থাকে।
        fun pickFrom(candidates: List<SelectionUnit>, remaining: IntArray, used: MutableSet<String>) {
            if (remaining[0] <= 0) return
            val filtered = candidates.filter { it.unitKey !in used }
            val ordered = filtered
                .groupBy { usage[it.unitKey] ?: 0 }   // কম usage আগে
                .toSortedMap()
                .values
                .flatMap { it.shuffled(rnd) }
            for (u in ordered) {
                if (remaining[0] <= 0) return
                if (u.unitKey in used) continue
                if (u.size > remaining[0]) continue    // এই রাউন্ডে জায়গা নেই — group ভাঙা যাবে না
                used.add(u.unitKey)
                remaining[0] -= u.size
            }
        }

        val tests = (1..count).map { testNum ->
            val used = LinkedHashSet<String>()

            val importantRatio = importantRatioRange.start +
                rnd.nextDouble() * (importantRatioRange.endInclusive - importantRatioRange.start)
            val wantImportant = (perTest * importantRatio).toInt().coerceIn(0, perTest)

            var mainRemaining = perTest
            if (importantUnits.isNotEmpty()) {
                val importantRemaining = intArrayOf(wantImportant)
                pickFrom(importantUnits, importantRemaining, used)
                mainRemaining = perTest - (wantImportant - importantRemaining[0])
            }
            pickFrom(units, intArrayOf(mainRemaining), used)

            val keys = mutableListOf<String>()
            used.forEach { unitKey ->
                usage[unitKey] = (usage[unitKey] ?: 0) + 1
                keys.addAll(unitMap.getValue(unitKey).sourceKeys)
            }
            GeneratedTest(testNum, keys)
        }

        return GenerateResult(tests, warning)
    }
}
