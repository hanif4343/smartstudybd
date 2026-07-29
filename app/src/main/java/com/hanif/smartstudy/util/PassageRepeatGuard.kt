package com.hanif.smartstudy.util

/**
 * প্যাসেজ-পুনরাবৃত্তি এড়ানোর গার্ড — "শাফল-ব্যাগ" পদ্ধতিতে কাজ করে:
 *
 *  ১. পুলের সব ইনডেক্স (0 until poolSize) একবার এলোমেলো (shuffle) করে একটা "ব্যাগ"-এ রাখা হয়।
 *  ২. next() কল হলে ব্যাগ থেকে একটা ইনডেক্স বের করে দেওয়া হয় (আর ব্যাগে থাকে না)।
 *  ৩. ব্যাগ খালি হয়ে গেলে (মানে পুলের সব ক'টা প্যাসেজ একবার করে দেখানো হয়ে গেছে),
 *     আবার নতুন করে শাফল করে রিফিল করা হয়।
 *
 * এই পদ্ধতির সুবিধা (সাধারণ pure-random pool.random()-এর চেয়ে ভালো):
 *  - পুলের প্রতিটা আইটেম "পরের রাউন্ড" শুরুর আগে অন্তত একবার দেখানো নিশ্চিত হয়
 *    (pure random হলে পরপর কয়েকবার একই প্যাসেজও আসতে পারত — সেটাও অবাঞ্ছিত)।
 *  - কখনো প্রেডিক্টেবল/সিকোয়েন্সিয়াল ক্রম আসে না (আগের বাগ যেটা ছিল)।
 *  - টানা দুইবার হুবহু একই প্যাসেজ (rollover-এর মুহূর্তে) সাধারণত এড়িয়ে যায় (নিচে দেখো)।
 *
 * ব্যবহার: TypingPracticeScreen-এ `remember { PassageRepeatGuard() }` দিয়ে একটা instance
 * রাখা, আর প্রতিটা "পরের প্যাসেজে যাও" মুহূর্তে —
 *
 *     val nextIdx = passageGuard.next(pool.size, passageIndex)
 *
 * — এটা দিয়েই আগের `(passageIndex + 1).mod(pool.size)` রিপ্লেস করা।
 *
 * নোট: পুলের আকার (difficulty/language বদলালে) পরিবর্তন হলে গার্ড নিজে থেকেই detect
 * করে নতুন করে শুরু করে (lastPoolSize চেক) — আলাদা করে reset() কল করা বাধ্যতামূলক না,
 * তবে ইচ্ছাকৃতভাবে (যেমন মোড বদলানোর সময়) চাইলে reset() ডেকে দেওয়া যায়।
 */
class PassageRepeatGuard {

    private var bag: MutableList<Int> = mutableListOf()
    private var lastPoolSize: Int = -1

    /**
     * পরের প্যাসেজের ইনডেক্স বের করে দেয়।
     * @param poolSize বর্তমান প্যাসেজ-পুলের আকার
     * @param currentIndex এই মুহূর্তে যেটা দেখানো হচ্ছে (টানা-দুইবার-রিপিট এড়াতে ব্যবহৃত হয়)
     */
    fun next(poolSize: Int, currentIndex: Int): Int {
        if (poolSize <= 1) return 0

        if (lastPoolSize != poolSize) {
            // পুল বদলে গেছে (difficulty/language সুইচ, নতুন কনটেন্ট সিঙ্ক ইত্যাদি) —
            // পুরনো ব্যাগ আর বৈধ না, নতুন করে শুরু করা হলো
            reset(poolSize)
        }

        if (bag.isEmpty()) {
            refill(poolSize)
        }

        var picked = bag.removeAt(bag.lastIndex)

        // টানা দুইবার হুবহু একই প্যাসেজ (আগেরটা আর এইটা) এড়ানো — শুধুমাত্র তখনই সম্ভব
        // যখন ব্যাগে এখনো বিকল্প কিছু আছে (আকার ২+ পুলে সাধারণত থাকবে)
        if (picked == currentIndex && bag.isNotEmpty()) {
            val alternative = bag.removeAt(bag.lastIndex)
            bag.add(picked)      // বাদ-পড়া ইনডেক্সটা ব্যাগে ফেরত, পরের রাউন্ডে আসবে
            picked = alternative
        }

        return picked
    }

    /** পুল বদলালে বা মোড বদলালে (নতুন সেশন শুরু) স্বেচ্ছায় রিসেট করার জন্য */
    fun reset(poolSize: Int = lastPoolSize) {
        lastPoolSize = poolSize
        refill(poolSize)
    }

    private fun refill(poolSize: Int) {
        bag = (0 until poolSize).toMutableList().apply { shuffle() }
    }
}
