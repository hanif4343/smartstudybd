package com.hanif.smartstudy.data.local

import kotlin.random.Random

// ── Routine স্মার্ট প্ল্যান — শুধু বাছাইয়ের যুক্তি (কোনো স্টোরেজ/UI নেই, তাই সহজে যাচাইযোগ্য) ──
// নিয়ম (মিশ্রণ): অর্ধেক "দুর্বল" টপিক (যেখানে এখনো ভুল প্রশ্ন জমে আছে), বাকি অর্ধেক "নতুন"
// টপিক (গত ১৪ দিনে প্ল্যানে আসেনি, বিষয় ঘুরিয়ে ঘুরিয়ে)। আগে থেকে রুটিনে থাকা টপিক বাদ।
object RoutineSmartPlanner {

    data class Topic(val subject: String, val subTopic: String)
    data class Pick(val topic: Topic, val weak: Boolean, val wrongCount: Int)

    fun plan(
        allTopics   : List<Topic>,
        wrongCounts : Map<Topic, Int>,
        recent      : Set<Topic>,
        existing    : Set<Topic>,
        count       : Int = 4,
        seed        : Long = 0L
    ): List<Pick> {
        if (count <= 0 || allTopics.isEmpty()) return emptyList()
        val allSet = allTopics.toSet()

        val weakSorted = wrongCounts.entries
            .filter { it.key in allSet && it.key !in existing && it.value > 0 }
            .sortedByDescending { it.value }

        val weakTarget = minOf(count / 2, weakSorted.size)
        val weakPicks  = weakSorted.take(weakTarget).map { Pick(it.key, true, it.value) }.toMutableList()
        val taken      = weakPicks.map { it.topic }.toMutableSet()

        // নতুন টপিক: সাম্প্রতিক নয়, রুটিনে নেই; না পেলে "সাম্প্রতিক নয়" শর্ত শিথিল
        var pool = allTopics.filter { it !in taken && it !in existing && it !in recent }
        if (pool.isEmpty()) pool = allTopics.filter { it !in taken && it !in existing }

        // বিষয় ঘুরিয়ে (round-robin) — একই বিষয়ের ভেতর থেকে পরপর না
        val rnd = Random(seed)
        val bySubject = pool.groupBy { it.subject }
            .mapValues { it.value.shuffled(rnd).toMutableList() }
            .toList().shuffled(rnd).toMutableList()
        val fresh = mutableListOf<Pick>()
        val need  = count - weakPicks.size
        while (fresh.size < need && bySubject.any { it.second.isNotEmpty() }) {
            for ((_, list) in bySubject) {
                if (fresh.size >= need) break
                if (list.isNotEmpty()) fresh.add(Pick(list.removeAt(0), false, 0))
            }
        }

        // নতুন টপিক কম পড়লে বাকি দুর্বল টপিক দিয়ে ভরাট
        if (weakPicks.size + fresh.size < count) {
            for (e in weakSorted) {
                if (weakPicks.size + fresh.size >= count) break
                if (e.key !in taken) { weakPicks.add(Pick(e.key, true, e.value)); taken.add(e.key) }
            }
        }
        return weakPicks + fresh
    }
}
