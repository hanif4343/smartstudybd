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
        // ইউনিক sourceKey রাখা — একই id দুই sheet-এ থাকলেও conflict হবে না
        val distinctPool = pool.distinctBy { it.sourceKey() }
        val importantPool = distinctPool.filter { it.isImportant }

        val warning = if (distinctPool.size < perTest)
            "⚠️ এই subject-এ মোট ${distinctPool.size}টি প্রশ্ন আছে, কিন্তু প্রতি টেস্টে $perTest টি চাওয়া হয়েছে — " +
            "প্রতিটা টেস্টে যতগুলো সম্ভব ততগুলোই থাকবে (repeat বাধ্যতামূলক আলাদা টেস্টগুলোর মধ্যে)"
        else null

        // usage[key] = কতবার আগের generated টেস্টগুলোতে এসেছে — কম ব্যবহৃতকে প্রাধান্য দিতে
        val usage = HashMap<String, Int>().apply { distinctPool.forEach { put(it.sourceKey(), 0) } }

        // কম-ব্যবহৃত-আগে ক্রমে বাছাই করে candidates থেকে n টা নেয় (already `used` বাদ দিয়ে)
        fun pickFrom(candidates: List<QuestionItem>, n: Int, used: MutableSet<String>) {
            if (n <= 0) return
            val remaining = candidates.filter { it.sourceKey() !in used }
            val ordered = remaining
                .groupBy { usage[it.sourceKey()] ?: 0 }   // কম usage আগে
                .toSortedMap()
                .values
                .flatMap { it.shuffled(rnd) }
            ordered.take(n).forEach { used.add(it.sourceKey()) }
        }

        val tests = (1..count).map { testNum ->
            val used = LinkedHashSet<String>()

            val importantRatio = importantRatioRange.start +
                rnd.nextDouble() * (importantRatioRange.endInclusive - importantRatioRange.start)
            val wantImportant = (perTest * importantRatio).toInt().coerceIn(0, perTest)

            if (importantPool.isNotEmpty()) pickFrom(importantPool, wantImportant, used)
            pickFrom(distinctPool, perTest - used.size, used)

            val keys = used.toList()
            keys.forEach { usage[it] = (usage[it] ?: 0) + 1 }
            GeneratedTest(testNum, keys)
        }

        return GenerateResult(tests, warning)
    }
}
