# 🚨 READ ME FIRST — SmartStudyBD কোড এডিট করার আগে অবশ্যই পড়ুন

এই ফাইলটা AI (Claude/ChatGPT/অন্য যেকোনো) এবং যেকোনো মানুষ — যিনিই এই কোড এডিট
করতে যাচ্ছেন, তার জন্য। App owner (আমি) কোনো professional app developer না —
AI-এর সাহায্যে এই অ্যাপ বানানো/মেইনটেইন করা হয়। তাই এই ফাইলে লেখা নিয়মগুলো
**কঠোরভাবে মেনে চলা বাধ্যতামূলক**, কোনো shortcut নেওয়া যাবে না।

---

## ১. সিস্টেম আর্কিটেকচার (সংক্ষেপে)

```
Google Sheet (master DB)
      │  GAS (Google Apps Script) — একমাত্র গেটওয়ে
      ▼
Firebase RTDB (user/order/live)  +  GitHub CDN (manifest.json + per-topic JSON)
      │
      ▼
Student App (Android, Kotlin, Room local DB + cache)
```

- **প্রশ্ন/কনটেন্ট** আসে GitHub CDN থেকে (hash-ভিত্তিক ক্যাশ) অথবা সরাসরি GAS থেকে
  (`GasContentService.kt`, `ArchiveGasService.kt`) — এটাই এখন ডিফল্ট মোড।
- **ইউজার অ্যাকাউন্ট, নোটিফিকেশন, অর্ডার/সিরিয়াল** আসে Firebase RTDB থেকে।
- **কনটেন্ট (প্রশ্ন) আপডেট করতে APK আপডেট লাগে না** — Sheet-এ লিখে Publish করলেই
  স্বয়ংক্রিয়ভাবে সব ইউজারের কাছে পৌঁছে যায়। **শুধু কোড/UI/ফিচার বদলালে নতুন APK
  release করতে হয়।**

---

## ২. এই ৫টা জিনিস কখনো ভুল করেও বদলাবেন না

1. **`applicationId` (`com.hanif.smartstudy`)** — বদলালে এটা "নতুন অ্যাপ" হয়ে
   যাবে, পুরনো অ্যাপের সাথে সম্পর্ক থাকবে না, সব ইউজার তাদের ডেটা/অ্যাপ হারাবে।
2. **Signing keystore** — একই key দিয়ে সাইন না করলে ইউজার নতুন APK-ই ইনস্টল করতে
   পারবে না ("conflicts with existing package" error) — সমাধান শুধু uninstall +
   reinstall, যাতে লোকাল ডেটা মুছে যায়। keystore ফাইল একাধিক জায়গায় ব্যাকআপ রাখুন।
3. **`GAS_URL` / `GAS_SECRET`** (BuildConfig) — ভুল করে বদলে/মুছে ফেললে পুরো
   অ্যাপের কনটেন্ট লোড হওয়া বন্ধ হয়ে যাবে সব ইউজারের জন্য সাথে সাথে।
4. **Room database entity/schema** (`SubjectEntity`, `TopicEntity`, ইত্যাদি) —
   বিনা migration-এ বদলালে বা `fallbackToDestructiveMigration()` ব্যবহার করলে
   Room পুরনো লোকাল ডেটাবেস **মুছে ফেলে** — ইউজারের লোকাল প্রগ্রেস/ক্যাশ চলে যাবে।
5. **GAS script-এর action নাম/প্যারামিটার** (`code_updated.gs`) — এটা বদলালে
   App-সাইডের সার্ভিস ফাইলগুলোর সাথে একসাথে/সিঙ্কে বদলাতে হবে, নাহলে সেই মুহূর্তেই
   সব ইউজারের ডেটা লোড বন্ধ হয়ে যাবে।

---

## ৩. প্রতিটা কোড এডিটের আগে AI-কে যা বলা উচিত (checklist)

- [ ] এই বদলে কি Room DB schema বদলাচ্ছে? বদলালে migration লিখেছে কিনা কনফার্ম করুন।
- [ ] এই বদলে কি GAS action/response format বদলাচ্ছে? App আর GAS দুই দিকই একসাথে
      আপডেট হচ্ছে কিনা।
- [ ] এই বদলে কি Firebase node স্ট্রাকচার বদলাচ্ছে? existing user data প্রভাবিত
      হচ্ছে কিনা।
- [ ] `versionCode` বাড়ানো হয়েছে কিনা (নতুন APK রিলিজ করলে)।
- [ ] `applicationId` এবং signing config অপরিবর্তিত আছে কিনা।
- [ ] শুধু যেটুকু ফিচার/ফাইল বদলানোর দরকার সেটুকুই বদলানো হয়েছে কিনা — অপ্রয়োজনীয়
      "রিফ্যাক্টর"/অন্য ফাইল টাচ করা এড়িয়ে চলুন (ঝুঁকি কমাতে)।

---

## ৪. রিলিজ করার আগে (নতুন APK)

1. নিজের ফোনে/emulator-এ ইনস্টল করে অন্তত এই ফ্লো টেস্ট করুন: লগইন → হোম →
   কুইজ/QBank/Study খোলা → রেজাল্ট দেখা → নোটিফিকেশন।
2. আগের কাজ-করা APK কপি নিরাপদ জায়গায় রাখুন (rollback-এর জন্য)।
3. `versionCode` +1 বাড়ান, changelog (বাংলায়, ২-৩ লাইন) লিখুন।
4. APK-এর SHA-256 checksum বের করে version.json/manifest-এ বসান।
5. সম্ভব হলে প্রথমে অল্প কিছু ইউজারকে দিন, সমস্যা না পেলে সবাইকে দিন।
6. ঘন ঘন ছোট আপডেট এড়িয়ে চলুন — ফিচার/ফিক্স জমিয়ে সপ্তাহে ১-২ বার রিলিজ করাই ভালো।
   কনটেন্ট (প্রশ্ন) আপডেটের জন্য APK release লাগবেই না — সেটা এমনিতেই রিয়েলটাইম।

---

## ৫. In-app Update (WhatsApp APK পাঠানোর বদলে)

লক্ষ্য: ইউজারকে WhatsApp-এ APK পাঠানো লাগবে না — অ্যাপ নিজেই নতুন ভার্সন চেক
করে ভেতর থেকে ডাউনলোড + install prompt দেখাবে।

- একটা `version.json` (GitHub raw বা GAS action) রাখুন:
  `{ "versionCode": 4, "versionName": "1.4", "apkUrl": "...", "sha256": "...",
  "changelog": "...", "forceUpdate": false }`
- App চালু হলে ব্যাকগ্রাউন্ডে এটা চেক করবে, নিজের `BuildConfig.VERSION_CODE`-এর
  সাথে তুলনা করবে।
- নতুন থাকলে বাংলায় changelog সহ একটা ডায়ালগ দেখাবে — "Update" চাপলে APK
  ডাউনলোড হবে, checksum মিলিয়ে `REQUEST_INSTALL_PACKAGES` পারমিশন +
  `FileProvider` দিয়ে Android-এর built-in installer খুলবে — ইউজার শুধু
  "Install" ট্যাপ করবে (সম্পূর্ণ silent install সম্ভব না, Android security-র
  কারণে অন্তত এক ট্যাপ লাগবেই)।
- version check ব্যর্থ হলে/নেট না থাকলে অ্যাপ যেন স্বাভাবিকভাবেই চলতে থাকে —
  আটকে যাওয়া চলবে না।
- `forceUpdate = true` শুধু জরুরি সিকিউরিটি/ক্রিটিক্যাল ফিক্সের জন্য — সাধারণত
  optional রাখুন।

---

## ৬. কিছু ভুল হয়ে গেলে

- **ডেটা লোড বন্ধ হয়ে গেছে মনে হলে** → প্রথমে চেক করুন GAS_URL/GAS_SECRET ঠিক
  আছে কিনা, GAS script deploy আপ-টু-ডেট কিনা।
- **অ্যাপ ইনস্টল হচ্ছে না নতুন APK** → signing key মিলছে কিনা চেক করুন।
- **কোনো ইউজারের ডেটা/প্রগ্রেস হারিয়ে গেছে অভিযোগ পেলে** → সাম্প্রতিক কোন কমিটে
  Room schema বা Firebase node বদলেছে কিনা খুঁজে বের করুন।
