# SmartStudyBD — ফিচার অনুযায়ী ফাইল ম্যাপ

> কোন ফিচারের জন্য কোন ফাইল ঘাঁটতে হবে, সেটা দ্রুত খুঁজে পাওয়ার জন্য এই নোট। প্রতিটা সেকশনে ফাইলের path + এক লাইনে কী কাজ করে সেটা লেখা আছে। প্যাকেজ প্রিফিক্স সবখানে `app/src/main/java/com/hanif/smartstudy/`।

---

## 🎙️ Viva Mode (ভয়েস মৌখিক পরীক্ষা)
| ফাইল | কাজ |
|---|---|
| `viewmodel/VivaViewModel.kt` | পুরো সেশনের state machine — বিষয়/টপিক জিজ্ঞাসা, random প্রশ্ন, গ্রেডিং, স্কোর |
| `ui/viva/VivaScreen.kt` | UI — মাইক/টাইপ ইনপুট, প্রশ্ন কার্ড, ফিডব্যাক ব্যাজ, সামারি স্ক্রিন |
| `data/remote/WrittenAnswerAiService.kt` | `resolveFromList()` (বিষয়/টপিক ভয়েস-ম্যাচিং) + `gradeVivaAnswer()` (voice-aware গ্রেডিং) — এই একই ফাইলে written-answer গ্রেডিংও আছে (নিচে দেখো) |
| `data/model/TestHistoryModels.kt` | `mode = "VIVA"` সাপোর্ট — Profile-এর Test History-তে দেখানোর জন্য |
| `ui/home/HomeScreen.kt` | Home-এ "🎙️ Viva Mode" কার্ড (Practice সেকশন) |
| `ui/main/MainScreen.kt` | `showViva` state দিয়ে VivaScreen ওপেন/ক্লোজ করা |

---

## 🤖 প্রশ্ন-ভিত্তিক ভয়েস AI চ্যাট (Quiz/QBank/Study প্রতিটা প্রশ্নে)
| ফাইল | কাজ |
|---|---|
| `viewmodel/QuestionVoiceAiViewModel.kt` | প্রতি-প্রশ্ন চ্যাট state, TTS অটো-স্পিক, race-condition-সেফ (unique key + job cancel) |
| `ui/aichat/QuestionVoiceAiSheet.kt` | ফুল-স্ক্রিন ডায়ালগ UI — প্রশ্ন কার্ড, মাইক, "একই ক্যাটাগরির আরও দেখুন", "মূল বিষয়বস্তু জানুন" বাটন |
| `data/remote/AiChatService.kt` | Groq→Mistral→Cerebras→Gemini fallback দিয়ে টেক্সট চ্যাট (contextPrefix সাপোর্ট সহ) |
| `ui/shared/SharedComponents.kt` | `QuestionCard`-এ 🤖 বাটন (onAskAi param) |
| `ui/quiz/QuestionListScreen.kt` | voiceAiIdx state, related-questions ফিল্টার, "পরের প্রশ্ন" নেভিগেশন |

## 💬 সাধারণ AI Chat (Doubt Solver, standalone)
| ফাইল | কাজ |
|---|---|
| `viewmodel/AiChatViewModel.kt` | সাধারণ প্রশ্নোত্তর চ্যাটের state |
| `ui/aichat/AiChatScreen.kt` | UI (Home থেকে "AI Chat" কার্ড দিয়ে খোলে) |
| `data/remote/AiChatService.kt` | (উপরেরটাই শেয়ার্ড ব্যাকএন্ড) |

## ✍️ Written উত্তর AI অটো-চেক (Study রিকল মোড)
| ফাইল | কাজ |
|---|---|
| `data/remote/WrittenAnswerAiService.kt` | `gradeWrittenAnswer()` (সঠিক/ভুল বাইনারি রায়) + `explainMistake()` |
| `data/model/AiApiKeys.kt` | Groq/Mistral/Cerebras/Gemini key মডেল (Settings-এ ইউজার নিজে সেভ করে) |
| `ui/menu/SettingsScreen.kt` | API key ইনপুট ফর্ম |

---

## 🔊 Text-to-Speech (Read Aloud + Viva/AI Chat voice)
| ফাইল | কাজ |
|---|---|
| `util/TtsManager.kt` | সিঙ্গেলটন TTS ইঞ্জিন — বাংলা/ইংরেজি মিক্সড টেক্সট সেগমেন্ট করে পড়া, unified pitch (gender-consistency fix) |
| `ui/shared/SmartTextToolbar.kt` | কাস্টম সিলেকশন টুলবার (Copy/Share/Search/**Read Aloud**) |

## 🎤 Voice ইনপুট (STT)
> কোনো আলাদা ফাইল নেই — প্রতিটা ভয়েস ফিচার নিজের কম্পোজেবলেই সরাসরি Android-এর `RecognizerIntent` (system speech dialog) কল করে, তাই `RECORD_AUDIO` পারমিশন লাগে না। দেখো: `QuestionVoiceAiSheet.kt`, `VivaScreen.kt`।

---

## 📚 Quiz / QBank / Study (মূল কনটেন্ট ইঞ্জিন — তিনটাই একই কোড শেয়ার করে)
| ফাইল | কাজ |
|---|---|
| `viewmodel/QuizViewModel.kt` | Quiz/QBank/Study তিনটার জন্যই একটাই ViewModel (mode প্যারামিটার দিয়ে আলাদা হয়) — উত্তর চেক, XP, admin edit/delete/rename/add রাউটিং |
| `ui/quiz/CoreScreen.kt` | Subject→SubTopic→QuestionList নেভিগেশনের রুট কম্পোজেবল |
| `ui/quiz/SubjectListScreen.kt` | Subject/SubTopic লিস্ট স্ক্রিন + reorder + Admin মেনু (Rename/Delete) |
| `ui/quiz/QuestionListScreen.kt` | প্রশ্নের লিস্ট, পেজিনেশন, উত্তর দেওয়া, রিপোর্ট, ভয়েস AI |
| `ui/quiz/ModelTestScreen.kt` | Mock Test মোড |
| `ui/quiz/ResultModal.kt` | পরীক্ষা শেষে ফলাফল পপআপ |
| `ui/shared/SharedComponents.kt` | `QuestionCard` (MCQ/Written/Study সব ধরনের প্রশ্নের UI) |
| `data/model/QuizModels.kt` | `QuizItem`, `QBankItem`, `StudyItem`, `QuestionItem` (unified model), `SubjectScore` |
| `data/local/QuestionDao.kt` / `QuestionEntity.kt` | Room DB — অফলাইন প্রশ্ন স্টোরেজ, `SubjectCount`/`SubTopicCount` কোয়েরি |
| `data/repository/ContentRepository.kt` | Firebase/GAS/Room — কনটেন্ট রিড/রাইটের কেন্দ্রীয় লজিক, XP award, streak |
| `data/remote/ContentFetchService.kt` | Firebase থেকে raw কনটেন্ট ফেচ |
| `util/ModelTestGenerator.kt` | Mock Test-এর জন্য র‍্যান্ডম প্রশ্ন বাছাই লজিক |

---

## 🛡️ Admin Power (এডিট/ডিলিট/রিনেম/অ্যাড — main app-এর ভেতর থেকে)
| ফাইল | কাজ |
|---|---|
| `viewmodel/MenuViewModel.kt` | `adminUpdateField`/`adminDeleteRow`/`adminAddRow`/`adminRenameSubjectOrTopic`/`adminDeleteSubjectOrTopic` — সব dual-write (Sheet primary + Firebase best-effort) |
| `data/remote/GasContentService.kt` | Google Sheet মোড — GAS Web App-এর সাথে সব admin write/read কল |
| `data/remote/FirebaseDataService.kt` | Firebase-সাইড admin write (rename/delete/update/normalizeFieldValue) |
| `ui/menu/sections/AdminPage.kt` | Admin Panel-এর ট্যাবসমূহ (Users, Notify, FCM, Reports, Add Question, Bulk Upload, Bulk Tag, Logs, Sync, Checklist) |
| `ui/quiz/SubjectListScreen.kt` | কনটেক্সচুয়াল Admin মেনু (Reorder/Rename/Delete) — Quiz/QBank/Study-এর ভেতর থেকেই |
| `data/local/PendingQueue.kt` | অফলাইনে করা admin edit-এর pending queue (পরে sync হয়) |
| `worker/SyncWorker.kt` | ব্যাকগ্রাউন্ডে pending edit sync |
| `data/model/DataSourceMode.kt` | Firebase বনাম Google Sheet — কনটেন্ট **read** কোথা থেকে হবে সেই সেটিং |

## 🌐 Admin Web App (আলাদা প্রজেক্ট — শুধু রেফারেন্সের জন্য)
> এটা এই Android প্রজেক্টের বাইরে (`smart-study-admin-app`) — React/Vite ওয়েব অ্যাপ। `sheetSave.js`, `DeleteTab.jsx`, `InlineEditModal.jsx`, `dataCache.js` — Google Sheet-primary dual-write প্যাটার্ন সেখানেই প্রথম বানানো হয়েছিল, এই Android app-এর admin power তারই কপি।

---

## ⌨️ Typing Practice
| ফাইল | কাজ |
|---|---|
| `ui/typing/TypingPracticeScreen.kt` | মূল টাইপিং প্র্যাকটিস UI |
| `ui/typing/TypingRaceScreen.kt` | Typing Race (মাল্টিপ্লেয়ার-স্টাইল) মোড |
| `util/TypingPassageProvider.kt` | প্যাসেজ সোর্সিং (Sheet fallback সহ) |
| `util/TypingAdaptiveContentProvider.kt` | দুর্বলতা-ভিত্তিক adaptive প্যাসেজ জেনারেশন |
| `util/TypingErrorAnalyzer.kt` | ভুল টাইপ বিশ্লেষণ |
| `util/TypingMistakeLogger.kt` | ভুলের লগ রাখা (Room-এ) |
| `util/HandKeyMap.kt` | বিজয় কীবোর্ডে কোন অক্ষর কোন হাতে (বাম/ডান) |
| `data/remote/TypingAiService.kt` | AI দিয়ে টাইপিং প্যাসেজ জেনারেট |
| `data/model/TypingRaceModels.kt` / `TypingSheetPassage.kt` | ডেটা মডেল |
| `data/local/TypingMistakeDao/Entity.kt`, `TypingHandStatsDao/Entity.kt`, `StudyTypingProgressDao/Entity.kt`, `TypingSheetPassageDao/Entity.kt`, `GeneratedPassageCacheDao/Entity.kt` | Room টেবিল (প্রগ্রেস, মিসটেক স্ট্যাটস, ক্যাশড প্যাসেজ) |
| `data/repository/TypingRaceRepository.kt` | Typing Race-এর Firebase লজিক |

---

## 🎯 Focus Mode
| ফাইল | কাজ |
|---|---|
| `focus/FocusModeConfig.kt` | ফোকাস মোডের কনফিগারেশন মডেল |
| `focus/FocusModeStore.kt` | স্টেট পার্সিস্টেন্স (DataStore) |
| `focus/FocusModePanel.kt` | ফোকাস মোড অন/অফ + সেটিংস UI |
| `focus/FocusModeInfoScreen.kt` | ফোকাস মোড ব্যাখ্যা স্ক্রিন |
| `focus/FocusNudgeSheet.kt` | Home/চ্যালেঞ্জ ট্যাপ করলে দেখানো নাজ শীট |
| `focus/FocusTodayCard.kt` | Home-এ আজকের ফোকাস কার্ড |
| `focus/FocusWarningOverlay.kt` | ফোকাস ভাঙলে সতর্কতা ওভারলে |
| `receiver/FocusReminderReceiver.kt` | AlarmManager-ভিত্তিক পড়ার রিমাইন্ডার (ব্যাকগ্রাউন্ড, নোটিফিকেশন) |
| `ui/main/MainScreen.kt` | `focusNudgeTabs` (Home/চ্যালেঞ্জ ইন্টারসেপ্ট — Menu বাদে) |

---

## 🏠 Home Screen
| ফাইল | কাজ |
|---|---|
| `ui/home/HomeScreen.kt` | মূল Home UI — সব কুইক-অ্যাকসেস কার্ড/গ্রিড |
| `viewmodel/HomeViewModel.kt` | Home-এর ডেটা (streak, XP, routine, notification) |
| `data/model/HomeModels.kt` | `LevelInfo` (XP লেভেল সিস্টেম) |
| `ui/home/NotificationsSheet.kt` | নোটিফিকেশন লিস্ট বটম-শিট |
| `ui/home/RoutineFocusSheet.kt` | রুটিন আইটেম বিস্তারিত শিট |
| `ui/home/WrongReviewSection.kt` | ভুল-করা প্রশ্ন রিভিউ সেকশন |
| `data/model/RoutineModels.kt`, `viewmodel/RoutineViewModel.kt`, `data/local/RoutineCache.kt` | দৈনিক রুটিন/সময়সূচি ফিচার |
| `widget/RoutineWidgetProvider.kt` | হোম-স্ক্রিন উইজেট (রুটিন) |
| `data/model/Achievements.kt` | ব্যাজ/অ্যাচিভমেন্ট মডেল |

---

## 👤 Menu / Profile / Settings
| ফাইল | কাজ |
|---|---|
| `ui/menu/MenuScreen.kt` | Menu ট্যাবের রুট (ট্যাব নেভিগেশন) |
| `ui/menu/ProfileScreen.kt`, `sections/ProfilePage.kt` | প্রোফাইল তথ্য/এডিট |
| `ui/menu/SettingsScreen.kt` | Data Source মোড, AI API key, নোটিফিকেশন সেটিংস |
| `ui/menu/StatsScreen.kt`, `sections/StatsPage.kt` | পরিসংখ্যান |
| `ui/menu/TestHistoryScreen.kt`, `viewmodel/TestHistoryViewModel.kt`, `data/local/TestHistoryCache.kt`, `data/model/TestHistoryModels.kt` | পরীক্ষার ইতিহাস (Quiz/QBank/Study/**Viva** সব মোড) |
| `ui/menu/StudyBuddyScreen.kt` | Study Buddy ফিচার UI |
| `ui/menu/PrivacyPolicyScreen.kt` | প্রাইভেসি পলিসি |
| `ui/menu/OtherScreens.kt`, `sections/OtherPages.kt` | ছোটখাটো মেনু সাব-পেজ |

## 👥 Study Buddy
| ফাইল | কাজ |
|---|---|
| `data/model/BuddyModels.kt` | `BuddyLink` ইত্যাদি মডেল (Firebase paths সহ ডকুমেন্টেড) |
| `viewmodel/BuddyViewModel.kt` | Buddy লজিক |
| `data/repository/BuddyRepository.kt` | Firebase রিড/রাইট |
| `ui/menu/StudyBuddyScreen.kt` | UI |

---

## ⚔️ Challenge / চ্যালেঞ্জ (মাল্টিপ্লেয়ার কুইজ)
| ফাইল | কাজ |
|---|---|
| `ui/challenge/ChallengeHubScreen.kt` | চ্যালেঞ্জ হোম |
| `ui/challenge/CreateChallengeScreen.kt` | নতুন চ্যালেঞ্জ বানানো |
| `ui/challenge/LobbyScreen.kt` | প্রতিপক্ষের জন্য অপেক্ষা |
| `ui/challenge/ChallengeExamScreen.kt` | চ্যালেঞ্জ চলাকালীন প্রশ্ন স্ক্রিন (এখানেও `QuestionCard` রিইউজ হয়) |
| `ui/challenge/ChallengeResultScreen.kt` | ফলাফল |
| `viewmodel/ChallengeViewModel.kt` | চ্যালেঞ্জ state |
| `data/model/ChallengeModels.kt` | মডেল |
| `data/repository/ChallengeRepository.kt` | Firebase লজিক |
| `ui/challenge/WeekendBattleScreen.kt`, `viewmodel/WeekendBattleViewModel.kt`, `data/model/WeekendBattleModels.kt`, `data/repository/WeekendBattleRepository.kt` | Weekend Battle (আলাদা সাব-ফিচার) |

---

## 🔐 Auth / Login / Session
| ফাইল | কাজ |
|---|---|
| `ui/auth/AuthScreen.kt` | লগইন/সাইনআপ UI |
| `viewmodel/AuthViewModel.kt` | লগইন লজিক |
| `data/remote/FirebaseAuthService.kt` | Firebase Auth API কল |
| `data/remote/FirebaseTokenProvider.kt` | Auth token ম্যানেজমেন্ট |
| `util/SessionManager.kt` | DataStore-এ ইউজার সেশন, API keys, Data Source মোড — সবচেয়ে বেশি জায়গা থেকে ব্যবহৃত ইউটিলিটি |
| `util/PhoneValidator.kt` | ফোন নম্বর ভ্যালিডেশন |
| `data/model/User.kt` | User মডেল |

---

## 🔔 নোটিফিকেশন / FCM
| ফাইল | কাজ |
|---|---|
| `service/SmartStudyFirebaseService.kt` | FCM push notification রিসিভার |
| `data/remote/FcmAdminService.kt` | Admin থেকে নোটিফিকেশন পাঠানো |
| `util/FcmHelper.kt` | টোকেন রেজিস্ট্রেশন |
| `worker/NotificationPollWorker.kt` | পোল-ভিত্তিক নোটিফিকেশন চেক |
| `data/model/AppNotification.kt` | মডেল |
| `receiver/ReminderReceiver.kt`, `receiver/RoutineItemReminderReceiver.kt`, `receiver/BootReceiver.kt` | অ্যালার্ম/বুট রিসিভার |
| `util/ReminderHelper.kt` | রিমাইন্ডার শিডিউল করা |

---

## 🗄️ ডেটাবেস / ক্যাশিং (কোর ইনফ্রাস্ট্রাকচার)
| ফাইল | কাজ |
|---|---|
| `data/local/AppDatabase.kt` | Room ডেটাবেস রুট (সব DAO-র entry point) |
| `data/local/ContentCache.kt` | Quiz/QBank/Study কনটেন্ট মেমরি-ক্যাশ |
| `data/local/EntityExtensions.kt` | Entity ↔ Model কনভার্সন |
| `data/local/LocalModelTestStore.kt`, `LocalTechniqueStore.kt` | ছোট লোকাল স্টোর |
| `data/local/CustomPassageDao/Entity.kt` | ইউজার-তৈরি কাস্টম প্যাসেজ |
| `data/model/UserTechnique.kt` | ইউজারের দেওয়া "মনে রাখার কৌশল" |
| `data/remote/RemoteServices.kt` | ImgBB আপলোড + `UserSyncService.fetchUser()` (Sheet fallback সহ) |
| `data/remote/GeminiService.kt` | সরাসরি Gemini API কল (আলাদা ইউজ-কেসে) |
| `data/remote/ImgBbService.kt` | ছবি আপলোড API |
| `util/AudienceFilter.kt` | Bulk notification-এর জন্য audience ফিল্টারিং |
| `util/RemoteLogger.kt` | দূরবর্তী ডিবাগ লগ |
| `util/ConnectivityObserver.kt` | নেটওয়ার্ক কানেক্টিভিটি চেক |
| `util/DeepLinkHandler.kt` | ডিপলিংক পার্সিং |
| `util/SoundManager.kt` | সাউন্ড এফেক্ট (সঠিক/ভুল বিপ ইত্যাদি) |
| `util/AdManager.kt`, `ui/ads/AdComposables.kt` | বিজ্ঞাপন |
| `util/ResultShareUtil.kt` | ফলাফল শেয়ার (ছবি/টেক্সট) |
| `ui/search/GlobalSearchScreen.kt` | সার্চ ফিচার |
| `ui/components/MediaViewer.kt` | ইমেজ/মিডিয়া ফুলস্ক্রিন ভিউয়ার |
| `ui/navigation/SmartStudyNavGraph.kt` | Nav graph (যদি ব্যবহৃত হয়) |
| `ui/theme/Theme.kt`, `Typography.kt` | রঙ/ফন্ট (NotoSansBengali, Indigo600 ইত্যাদি এখানে ডিফাইন্ড) |
| `MainActivity.kt` | Entry point activity |
| `SmartStudyApp.kt` | Application ক্লাস |

---

## 🧭 কীভাবে ব্যবহার করবেন
- নতুন ফিচার শুরু করার আগে এই ফাইলটা দেখে নিন — একই রকম existing ফিচারের ফাইলগুলো প্যাটার্ন হিসেবে কাজে লাগবে।
- `QuestionCard` (SharedComponents.kt), `TtsManager`, `SessionManager`, `ContentRepository` — এই ৪টা প্রায় সব ফিচারেই কোনো না কোনোভাবে ব্যবহৃত হয়, তাই এগুলো বদলালে সাবধানে সব caller চেক করা দরকার।
- এই ডকুমেন্টে নতুন ফিচার যোগ হলে (যেমন Viva Mode আজ যোগ হলো) এই ফাইলটাও আপডেট করে রাখা ভালো।
