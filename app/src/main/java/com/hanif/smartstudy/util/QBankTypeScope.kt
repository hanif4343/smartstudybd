package com.hanif.smartstudy.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * QBank তালিকায় "MCQ | লিখিত" দুই কলামের যে কলাম থেকে কার্ডে ট্যাপ করা হয়েছে সেটা মনে রাখে।
 *  • null = ভাগ নেই (আগের আচরণ: ভেতরে MCQ/লিখিত বার)
 *  • 0    = MCQ কলাম  → ভেতরের পদ-তালিকা ও প্রশ্ন শুধু MCQ
 *  • 1    = লিখিত কলাম → ভেতরের পদ-তালিকা ও প্রশ্ন শুধু লিখিত (পেপার-স্টাইলে)
 * তালিকা-স্ক্রিন (depth 0) আবার দেখা গেলে SubjectListScreen এটা null করে দেয়।
 * QBankCategory-এর মতোই Compose state, তাই পরিবর্তনে UI আপনা-আপনি আপডেট হয়।
 */
object QBankTypeScope {
    var part by mutableStateOf<Int?>(null)
}
