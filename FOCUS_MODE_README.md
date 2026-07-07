# 📋 ফোকাস মোড — ইমপ্লিমেন্টেশন নোট (Part ১ ও ২)

এই আপডেটে প্ল্যানের **১ (সাবজেক্ট বাছাই ও চালু করা)** এবং **২ (অ্যাপ ওপেন করলে
Warning স্ক্রিন)** — এই দুইটা অংশ যুক্ত করা হয়েছে। বাকি অংশ (নাজ, ব্যাকগ্রাউন্ড
নোটিফিকেশন, Menu প্যানেল) পরের ধাপে যুক্ত হবে (নিচে "বাকি আছে" অংশ দেখুন)।

## 🎯 মাস্টার সুইচ

`FocusModeConfig.ENABLED = false` করে দিলেই পুরো ফিচার (warning overlay সহ)
সাথে সাথে বন্ধ হয়ে যাবে, বাকি অ্যাপ স্বাভাবিক থাকবে।

## 🆕 নতুন ফাইল (সব `com.hanif.smartstudy.focus` প্যাকেজে, একদম আলাদা)

| ফাইল | কাজ |
|---|---|
| `FocusModeConfig.kt` | মাস্টার সুইচ (`ENABLED`), `ADMIN_ONLY`, hard-cap দিন সংখ্যা, ভবিষ্যতের notification interval — সব constant একজায়গায় |
| `FocusModeStore.kt` | DataStore-ভিত্তিক persistence — সাবজেক্ট, পরীক্ষার তারিখ, enabled flag, activatedAt। `FocusModeState.isEffectivelyActive()` — এই একটামাত্র ফাংশনই ঠিক করে warning/nudge/notification আসলে দেখানো উচিত কিনা (master switch + enabled + expiry + ৭ দিনের hard cap — সব চেক করে) |
| `FocusWarningOverlay.kt` | অ্যাপ খোলার সাথে সাথে দেখা যাওয়া ফুল-স্ক্রিন ওভারলে (CongratsOverlay-এর bounce/pulse প্যাটার্নে)। "এখন না" বাটন সবসময় থাকে, জোর করে আটকায় না |
| `FocusTodayCard.kt` | Study ট্যাবের উপরে "🎯 আজ ফোকাস" কার্ড + ট্যাপ করলে খোলা bottom sheet — সাবজেক্ট লিস্ট থেকে বেছে নেওয়া, আজ/আগামীকাল/কাস্টম ডেট পিকার, চালু/বন্ধ বাটন |

## ✏️ সামান্য পরিবর্তিত ফাইল (২-৩ লাইনের hook)

| ফাইল | কী যোগ হলো |
|---|---|
| `ui/quiz/SubjectListScreen.kt` | Study মোডে, Admin হলে, সাবজেক্ট লিস্টের একদম উপরে `FocusTodayCard(...)` item যোগ হয়েছে |
| `ui/main/MainScreen.kt` | (১) `Box` দিয়ে Scaffold-কে wrap করা হয়েছে যাতে overlay উপরে বসতে পারে (২) `LaunchedEffect(Unit)`-এ একবার `FocusModeStore.getState()` চেক করে `focusWarning` state সেট করা হয় (৩) নিচে `FocusWarningOverlay` রেন্ডার হয় — "এখনই পড়া শুরু করি" চাপলে সরাসরি Study ট্যাবে focus-subject-এ নিয়ে যায় (`studyViewModel.navigateToSubject(...)`, HomeScreen-এর `onOpenStudy`-এর একই প্যাটার্ন) |

কোনো এক্সিস্টিং ফাইলের বিদ্যমান লজিক মোছা/বদলানো হয়নি — শুধু নতুন কোড যোগ করা
হয়েছে, তাই ভাঙার ঝুঁকি কম এবং দরকার হলে এই hook গুলো সহজে সরিয়ে ফেলা যাবে।

## 🔒 অডিয়েন্স

আপাতত শুধু Admin (`isAdmin() == true`) — সাধারণ ইউজার কার্ড বা ওভারলে কোনোটাই
দেখবে না। এটা `SubjectListScreen.kt`-এর `isAdmin` প্যারামিটার এবং
`FocusModeConfig.ADMIN_ONLY` (documentation constant, কলার নিজে চেক করে) দিয়ে
নিয়ন্ত্রিত।

## ✅ বাকি আছে (পরের ধাপে করা হবে)

- **Part ৩ — নাজ (nudge):** Home/চ্যালেঞ্জ/Menu ট্যাপ করলে ছোট bottom sheet
  দেখানো ("পড়ায় ফিরে যাই" / "ফোকাস মোড বন্ধ করো")। Study/Quiz/QBank/Wrong
  Review/Model Test কখনো ব্লক হবে না।
- **Part ৪ — ব্যাকগ্রাউন্ড নোটিফিকেশন:** অ্যাপ ব্যাকগ্রাউন্ডে গেলে প্রতি ১০
  মিনিটে notification (নতুন `FocusReminderReceiver.kt` + `SmartStudyApp.kt`/
  `MainActivity.kt`-এ foreground-tracker hook)। নোটিফিকেশন ট্যাপে
  `DeepLinkHandler.kt`-এ নতুন ডিপ-লিংক টাইপ দিয়ে সরাসরি focus-subject-এ যাওয়া।
- **Part ৫ — Menu প্যানেল:** `MenuScreen.kt`-এ ফোকাস মোডের স্ট্যাটাস (চালু/বন্ধ,
  কোন সাবজেক্ট, কবে অটো বন্ধ হবে) + "🔴 এখনই বন্ধ করো" বাটন। (`FocusModeStore.
  deactivate()` ফাংশন ইতিমধ্যে বানানো আছে, শুধু UI বাকি — Study ট্যাবের কার্ডের
  bottom sheet থেকেও আপাতত বন্ধ করা যায়)
- **অটো-এক্সপায়ারি reschedule:** এখন পরীক্ষার তারিখ পার হলে
  `isEffectivelyActive()` false রিটার্ন করে (তাই warning/card বন্ধ দেখাবে),
  কিন্তু এখনো কোনো background job নেই যেটা expired হলে সাথে সাথে notification/
  alarm বন্ধ করে দেয় — সেটা Part ৪-এর সাথেই যুক্ত হবে।
