# Smart Study — এই ডেলিভারিতে যা যা ফিক্স/যোগ হয়েছে

শুধু নতুন/এডিট হওয়া ফাইলগুলো `smartstudybd-main/` ফোল্ডারের ভেতরে একই আসল
পাথে আছে — সরাসরি আপনার প্রজেক্টের ওপর কপি-পেস্ট করে বসিয়ে দিতে পারবেন
(overwrite)। GAS ব্যাকএন্ড ফাইলটা (`code_updated_final-1.gs`) আলাদাভাবে
দেওয়া আছে, ওটা আপনার Google Apps Script প্রজেক্টে replace করে Deploy করতে হবে।

## ২. অ্যাপ ওপেন হতে দেরি (১৫-২০ সেকেন্ড)
- **`ui/splash/SplashScreen.kt`** — হার্ডকোড করা `delay(2500)` কমিয়ে `450ms`
  করা হয়েছে (আগে শুধু লোগো দেখানোর জন্য প্রতিবার আড়াই সেকেন্ড জোর করে
  বসিয়ে রাখা হতো)।
- **`data/local/ContentCache.kt`** — আসল কারণ এখানেই ছিল: হাজার হাজার
  প্রশ্নের পুরো Quiz+QBank+Study JSON serialize/parse (Gson) করার ভারী কাজ
  আগে Main/UI থ্রেডে চলত, তাই অ্যাপ প্রতিবার খোলার সময় UI ফ্রিজ হয়ে যেত। এখন
  `withContext(Dispatchers.IO)` দিয়ে background থ্রেডে সরানো হয়েছে।

## ৩. QBank-এও Quiz-এর মতো Admin ফিচার (Move/Delete/Rename — পদবী/প্রতিষ্ঠান/সাল)
- **`code_updated_final-1.gs`** — নতুন backend actions: `mergeReferenceItem`
  (Move/merge), `renameQBankYear`, `updateReferenceField` (ইমুজির জন্য, #৪
  দেখুন)। বিদ্যমান `renameReferenceItem`/`deleteReferenceItem` রিইউজ হয়েছে,
  আর Post/Institution ডিলিটে এখন Exam_Appearances-এর সংশ্লিষ্ট রো-ও
  cascade-delete হয়।
- **`data/remote/GasContentService.kt`** — এই নতুন actions-এর client wrapper।
- **`viewmodel/QuizViewModel.kt`** — `adminRenameQBankPost/Institution/Year`,
  `adminDeleteQBankPost/Institution/Year`, `adminMoveQBankPost/Institution`,
  `adminSetEmoji` ফাংশন।
- **`ui/quiz/CoreScreen.kt`** — পদবী/প্রতিষ্ঠান/সাল লিস্টে আগে
  `isAdmin = false` হার্ডকোড ছিল (ইচ্ছাকৃতভাবে বন্ধ রাখা), এখন Rename/Delete
  (টপ-লেভেল) + Rename/Delete/Move (নেস্টেড লিস্ট) চালু।

⚠️ **সীমাবদ্ধতা:** "সাল" কোনো নাম-ভিত্তিক reference-টেবিল না — QBank শিটের
প্রতিটা প্রশ্নের নিজস্ব `year` কলাম। তাই সাল Delete মানে **সেই সালের সব
QBank প্রশ্ন-ই ডিলিট হয়ে যাবে**, শুধু একটা লিংক না — ব্যবহারের সময় সাবধান।

## ৪. এডমিন ইমুজি পরিবর্তন (Subject/Topic/পদবী/প্রতিষ্ঠান)
- **`data/local/EmojiOverrideStore.kt`** (নতুন ফাইল) — লোকাল
  SharedPreferences-এ ইমুজি ওভাররাইড সেভ করে, তাই সেভ করার সাথে সাথেই
  UI-তে দেখা যায়।
- Subject/Post/Institution কার্ডের আইকনে ট্যাপ করলে (Admin মোডে) ছোট
  ডায়ালগ খোলে, নতুন ইমুজি বসানো যায়।
- ⚠️ **সীমাবদ্ধতা:** এই মুহূর্তে emoji Sheet-এ লেখা হয় (best-effort) কিন্তু
  `getReferenceData` action সেটা এখনো ফেরত দেয় না — তাই emoji আপাতত যে
  ডিভাইস থেকে সেট করা হয়েছে সেখানেই দেখা যাবে, অন্য ডিভাইস/রিইনস্টলে সিঙ্ক
  হবে না। ক্রস-ডিভাইস সিঙ্ক একটা ফলো-আপ (getReferenceData-এ emoji কলাম
  যোগ করা লাগবে)।

## ৬. Admin Edit বক্সে Update/Submit বাটন কিবোর্ডের নিচে চাপা পড়া
- **`ui/shared/SharedComponents.kt`** — `AdminFieldEditDialog`-এ
  Close/Update বাটন আগে সবার নিচে ছিল, এখন হেডারের ঠিক নিচে (টেক্সট-ফিল্ডের
  আগে) — কিবোর্ড যতই বড় হোক, বাটন সবসময় প্রথমেই দেখা যাবে।

---

## ⚠️ Merge-এ যে conflict পাওয়া গিয়েছিল ও ঠিক করা হয়েছে

`QuizViewModel.kt`-এর এই প্যাচটা main-এর একটা পুরনো ভার্সনের উপর বানানো
হয়েছিল, যেখানে `navigateToSubjectLazy()`-এর ভিতরে থাকা
**"Article: 74 প্রশ্ন দেখাতো, ভিতরে ঢুকলে ২৩টা"** বাগ-ফিক্স (per-sheet
`rowCountQuiz/rowCountQbank/rowCountStudy` অনুযায়ী সঠিক কাউন্ট বাছাই করার
লজিক) তখনও ছিল না। তাই সরাসরি এই ফাইলটা বসিয়ে দিলে main-এ আগে থেকে থাকা ওই
ফিক্সটা হারিয়ে যেত (silently reverted হয়ে যেত)। এই ডেলিভারিতে দুটোই — পুরনো
rowCount ফিক্স এবং নতুন QBank Admin/Emoji ফিচার — একসাথে মার্জ করে দেওয়া
হয়েছে, কিছুই হারায়নি।

বাকি সব ফাইল (SharedComponents, SplashScreen, CoreScreen,
SubjectListScreen, GasContentService, ContentCache, এবং qbank প্যাচের
তিনটা ফাইল) main-এর সাথে লাইন-বাই-লাইন মিলিয়ে দেখা হয়েছে — সেগুলোতে শুধু
addition/refactor, কোনো existing ফিক্স হারায়নি।

**সবচেয়ে জরুরি:** আমি এই পরিবর্তনগুলো Gradle/Android Studio দিয়ে কম্পাইল
করে টেস্ট করতে পারিনি (এই পরিবেশে Android SDK নেই), শুধু ম্যানুয়ালি ব্রেস/
প্যারেন ব্যালেন্স আর টাইপ-সিগনেচার মিলিয়ে দেখেছি। দয়া করে Android Studio-তে
build করে দেখে নেবেন, কোনো সমস্যা পেলে জানাবেন — ঠিক করে দেব।
