# SMART_STUDY_CLAUDE_CHECKLIST.md

Legend: [ ] not started · [~] in progress · [x] done · [!] manual action · [-] blocked

## PHASE 0 — AUDIT  (status: [~] first-pass done; deeper checks pending)
Scope: static code scan of `smartstudybd-main` zip (341 files). Nothing was changed. Nothing was built or run.

| # | Finding | Verdict |
|---|---------|---------|
| 1 | Stack is **Kotlin + Jetpack Compose**, Room v21, DataStore. Original prompt said Flutter — corrected in Master Prompt v2 | KEEP |
| 2 | Content comes from GitHub CDN + GAS (`CdnService.kt`, `GasContentService.kt`, `ContentFetchService.kt`) — matches desired architecture | KEEP |
| 3 | Firebase (`FirebaseDataService.kt`) used for users/orders/reports. No direct "question write to Firebase" found by name grep; admin question functions are marked deprecated/GAS-routed (`adminAddQuestion`) | MANUAL CHECK (Phase 2 deep trace) |
| 4 | **Bulk Upload** exists inside `ui/menu/sections/AdminPage.kt` (tab "⚡ Bulk Upload", `parseBulkEntry`) | REMOVE (Phase 1) |
| 5 | Admin screen reachable from `MenuScreen.kt` when `state.isAdmin` (`MenuNav.ADMIN`) | CHANGE — decide: remove, or keep hidden for you only |
| 6 | "Upload Report" — no exact match by name grep; may be inside AdminPage or GAS service | UNKNOWN (check in Phase 1) |
| 7 | `StudyBulkItem` in `QuestionListScreen.kt` is a Study-mode batch feature, probably NOT admin upload | MANUAL CHECK before touching |
| 8 | `AppDatabase.kt` uses `fallbackToDestructiveMigration()` (breaks README rule #4: can wipe local progress on version bump) | CHANGE (later, with migration plan) |
| 9 | No `source_qbank_id`-style field found | CHANGE (Phase 5) |
| 10 | `ExamAppearanceEntity.kt` exists | KEEP (Phase 6 check) |
| 11 | Sync/version: `TopicSyncEntity`, `CdnService`, `CdnFailureNotifier`, `SyncWorker` exist | UNKNOWN (Phase 4 deep check) |
| 12 | Question report: `QuizViewModel.reportQuestion` → `FirebaseDataService.reportQuestion` | KEEP / MANUAL CHECK |
| 13 | Pending: `PendingQueue.kt` (local queue) | MANUAL CHECK (Phase 8) |
| 14 | MCQ UI: `QuestionCard`, `McqOptions` in `ui/shared/SharedComponents.kt`; used from QuestionListScreen, GlobalSearchScreen, WrongReviewSection | CHANGE (Task MCQ-1) |
| 15 | Settings UI: `ui/menu/SettingsScreen.kt` (984 lines); prefs via `SessionManager` DataStore | KEEP (place to add selector) |
| 16 | `Challenge`, `Typing`, `Viva`, `AiChat`, `Archive`, `Focus` features exist — outside this plan's scope | KEEP |

Files likely needing changes in later phases: `AdminPage.kt`, `MenuScreen.kt`, `MenuViewModel.kt`, `GasContentService.kt`, `FirebaseDataService.kt`, `AppDatabase.kt`, `SharedComponents.kt`, `SettingsScreen.kt`, `SessionManager.kt`, `QuestionListScreen.kt`.

## TASK MCQ-1 — MCQ View: 2 choosable designs  [!] CODE WRITTEN — NOT BUILT/TESTED
- [x] `McqViewStyle` enum (CARD_MODERN default, COMPACT_LIST) + `LocalMcqViewStyle` — new file `ui/theme/McqViewStyle.kt`
- [x] DataStore key `mcq_view_style` + getter/flow/setter in `SessionManager.kt`
- [x] Provided app-wide via `SmartStudyTheme(mcqViewStyle=...)` in `Theme.kt` / `MainActivity.kt`
- [x] Settings card "📝 MCQ ভিউ" (2 radio options) in `SettingsScreen.kt` + `MenuViewModel.setMcqViewStyle`
- [x] `McqOptions` now dispatches to `McqOptionsCompact` / `McqOptionsCard` (old look unchanged) in `SharedComponents.kt`; all 3 call sites (Quiz/QBank list, Search, WrongReview) inherit it with no signature change
- [x] No Room/DB/Firebase/GAS change; same `QuestionItem` data
- [ ] VERIFIED BY BUILD — NOT DONE (no Android SDK here)
- [ ] VERIFIED ON PHONE — manual
- Known limit: `ChallengeExamScreen` and `ArchiveQuestionCard` use their own option UI, not changed
- Files changed: McqViewStyle.kt(new), Theme.kt, SessionManager.kt, MainActivity.kt, MenuViewModel.kt, SettingsScreen.kt, SharedComponents.kt

## TASK P1 — Remove Bulk Upload + New Question from student app  [!] CODE WRITTEN — NOT BUILT/TESTED
- [x] Removed "⚡ Bulk Upload" tab and helpers (AdminPage.kt, MenuViewModel.kt)
- [x] Removed "➕ নতুন প্রশ্ন" tab, `adminAddQuestion`, `clearAddQuestionMsg`, `loadAdminTaxonomy` + their state fields
- [x] Admin page now has only: ⏳ Sync · ✅ চেকলিস্ট
- [x] "Upload Report": not present in code
- [x] Kept on purpose: `adminAddRow`, `PendingQueue.enqueueAdminAdd`, `SyncWorker.syncAdminAdd`, `ContentRepository.addContentAndPersist` — so already-queued offline items can still drain. `addContentAndPersist` is now unused (dead; remove in cleanup phase)
- [ ] Verified by build — NOT DONE (user will build later)

## TASK P1b — Remove "⏱ আমার সময়" (My Time)  [!] CODE WRITTEN — NOT BUILT
- [x] Deleted: `ui/menu/StudyTimeScreen.kt`, `util/AppUsageTracker.kt`, `util/DeviceUsageStats.kt` (see FILES_TO_DELETE.txt)
- [x] `MenuScreen.kt`: removed menu row, `STUDY_TIME` nav, "studytime" deep link
- [x] `MainActivity.kt`: removed `AppUsageTracker.addMinutes` hook (other session recording untouched)
- [x] `AndroidManifest.xml`: removed `PACKAGE_USAGE_STATS` permission
- [x] Searched all sources: no leftover references

## TASK P2 — Firebase content separation  [~] IN PROGRESS
OWNER DECISION: admin edit / delete / move / rename inside the app MUST stay 100% working (owner is the admin and studies in the app). Do NOT remove them.
- [x] Removed dead `FirebaseDataService.adminAddQuestion`, `adminRenameSubjectOrTopic`, `adminDeleteBySubjectOrTopic` (zero callers). `MenuViewModel.adminRenameSubjectOrTopic` (GAS path used by MainScreen) UNTOUCHED
- [x] `SharedComponents.kt` edit/delete dialog Firebase fallback LEFT AS IS (callbacks from MainScreen are the normal path) — revisit only if testing shows it fires
- [ ] Verify `reportQuestion` writes only a report record
- [ ] MANUAL: Firebase Console — do old question nodes (Quiz/QBank/Study) still exist?
- [ ] MANUAL (after build): test edit, delete, move, rename, Sync tab, Checklist tab

## ROADMAP
1. [~] Phase 0 Audit  2. [!] MCQ-1 (build pending)  3. [!] Phase 1 Bulk + New Question removed (build pending)  4. [~] Phase 2 (admin edit/delete/move/rename KEPT by owner decision)  4. [ ] Firebase content separation  5. [ ] Sync  6. [ ] QBank→Quiz source ID  7. [ ] Exam appearance  8. [ ] Report/Pending  9. [ ] Error handling/offline  10. [ ] UI cleanup  11. [ ] Final audit

Next recommended task: finish Phase 2 verification (reportQuestion, Firebase nodes), then Phase 3/4 (Sync). Build + test everything at the end of this batch.

## TASK JUNK-1 — Bulk import: block AI chatter/template lines  [!] CODE DONE, NOT BUILT/TESTED
- Files changed: `ui/menu/sections/AdminPage.kt` (parser only)
- Now: only real `{{ ... }}` blocks are entries; `;;` separator supported; Gemini intro/outro text, the `{{প্রশ্ন;;অপ১;;…}}` template line, and MCQs with all-identical options are rejected.
- Verified: logic simulated in Python on sample text only. Kotlin NOT compiled.
- Remains: GAS `bulk_save_rows` server-side guard (optional); build + phone test.
- Note: this whole tab is scheduled for removal in Phase 1.
