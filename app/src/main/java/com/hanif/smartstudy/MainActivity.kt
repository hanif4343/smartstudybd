package com.hanif.smartstudy

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hanif.smartstudy.vision.VisionApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VisionApp() }
    }

    // পুরনো AuthScreen এই ফাংশনটা ডাকে (MainActivity-এর মেম্বার হিসেবে) — ভিশন
    // ব্রাঞ্চে পুরনো কোড কম্পাইল হওয়ার জন্য একই সিগনেচারের স্টাব রাখা হলো।
    // ভিশন অ্যাপে Google সাইন-ইন নেই, তাই সবসময় onError ডাকা হয়; ভিশন অ্যাপ
    // নিজে এই ফাংশন ব্যবহার করে না।
    fun startGoogleSignIn(
        onSuccess: (email: String, name: String, photoUrl: String, idToken: String) -> Unit,
        onError: (msg: String) -> Unit
    ) {
        onError("ভিশন সংস্করণে Google সাইন-ইন নেই")
    }
}
