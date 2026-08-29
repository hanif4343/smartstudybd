# 🔬 সাময়িক ডায়াগনস্টিক/টেস্ট কোড — ট্র্যাকিং লিস্ট

এই ফাইলে সেশনে যোগ করা **সব সাময়িক ডিবাগ-অনলি কোড** এর তালিকা আছে, যাতে
সমস্যাগুলো নিশ্চিতভাবে সমাধান হয়ে গেলে সহজে খুঁজে বের করে মুছে ফেলা যায়।
প্রতিটা এন্ট্রি খুঁজতে `grep -rn "🔬 DIAG"` চালালেই কোড-এ ঠিক লাইনটা পাওয়া যাবে।

**নিয়ম:** এই ফাইলটাকে Git-এ commit করে রাখা যেতে পারে (ট্র্যাকিংয়ের জন্য),
কিন্তু চূড়ান্ত/প্রোডাকশন রিলিজের আগে নিচের সবকটা আইটেম রিমুভ করে এই ফাইলটাও
মুছে ফেলা উচিত (অথবা "✅ REMOVED" হিসেবে মার্ক করে রাখা যেতে পারে ইতিহাসের জন্য)।

---

## ⏳ এখনো সক্রিয় (Active)

### 1. QBank পদবী/প্রতিষ্ঠান/সাল — mode/count/sample DIAG লাইন
- **ফাইল:** `app/src/main/java/com/hanif/smartstudy/ui/quiz/SubjectListScreen.kt`
- **কোথায়:** QBank ফিল্টার-বারের ঠিক নিচে, `showQBankFilterBar && isAdmin` হলে
- **দেখতে:** `🔬 DIAG mode=... count=... sample=...`
- **কেন যোগ হলো:** "পদবী ট্যাবে Subject কার্ড দেখাচ্ছে" রিপোর্টের রুট কারণ
  (পুরনো/আন-রিবিল্ট APK নাকি সত্যিকারের কোড-বাগ) নিশ্চিত করার জন্য।
- **স্ট্যাটাস:** ✅ কনফার্ম হয়ে গেছে সমস্যাটা পুরনো বিল্ডের ছিল, কোড ঠিকই ছিল —
  **এখন নিরাপদে মুছে ফেলা যায়**, শুধু Admin-কে দেখা যায় বলে ইউজারের ক্ষতি নেই।
- **মুছতে:** পুরো `if (showQBankFilterBar && isAdmin) { item { ... } }` ব্লক
  (কমেন্টসহ) বাদ দিন।

### 2. QBank multi-part প্রশ্নের groupId/groupHeading/formatStyle DIAG লাইন
- **ফাইল:** `app/src/main/java/com/hanif/smartstudy/ui/quiz/QBankExamPaperScreen.kt`
- **কোথায়:** `ExamSerialCard`-এর ভিতরে, `isAdmin && serial.size > 1` হলে
- **দেখতে:** `🔬 DIAG groupId=... groupHeading=... formatStyle=...`
- **কেন যোগ হলো:** "Sheet-এ group_heading ভরা থাকা সত্ত্বেও App-এ দেখাচ্ছে না"
  — ডেটা runtime-এ পৌঁছাচ্ছে কিনা যাচাই করার জন্য (Room schema fix-এর পর)।
- **স্ট্যাটাস:** 🔴 **এখনো active — root cause কনফার্ম হয়নি।**
- **মুছতে:** `ExamSerialCard`-এর শুরুতে DIAG `Text(...)` ব্লক + এই কমেন্ট বাদ
  দিন। সাথে `isAdmin` প্যারামিটারটাও (যদি আর অন্য কোথাও কাজে না লাগে)
  `QBankExamPaperScreen`/`ExamSerialCard`/`CoreScreen.kt`-এর কল-সাইট থেকে
  বাদ দেওয়া যায় — যদিও isAdmin থ্রেড-করা রাখাই ভালো, ভবিষ্যতে আরও admin-only
  ফিচারের জন্য দরকার হতে পারে।

---

## 🛠️ ডিবাগ-সহায়ক (স্থায়ী রাখার উদ্দেশ্যে বানানো, DIAG না — মুছতে হবে না)

এগুলো temp diagnostic না, বরং ইচ্ছাকৃতভাবে দীর্ঘমেয়াদী Admin-টুল হিসেবে
বানানো হয়েছে — production-এও Admin-এর জন্য দরকারি থাকবে:

- **Home হেডারের 🔄 "Force Full Resync" বাটন** (Admin-only, top-right) —
  `HomeScreen.kt` / `HomeViewModel.kt` / `ContentRepository.forceFullResync()`।
  Room cache clear করে CDN থেকে টাটকা ডেটা টানার জন্য — এটা রাখাই উচিত, ভবিষ্যতে
  content-sync সমস্যা ডিবাগ করতে কাজে লাগবে।
- **Pull-to-refresh (সব ইউজার)** — Subject/QBank লিস্ট স্ক্রিনে টেনে-নামানো,
  `SubjectListScreen.kt`-এর `PullToRefreshBox` + `QuizViewModel.refreshCurrentMode()`।
  এটা normal UX ফিচার, ডিবাগ-টুল না — রাখতেই হবে।

---

## কীভাবে যাচাই করবেন সব DIAG মোছা হয়েছে

```bash
grep -rn "🔬 DIAG" app/src/main/java/
```
কিছু না পেলে বুঝবেন সব temp diagnostic কোড পরিষ্কার।
