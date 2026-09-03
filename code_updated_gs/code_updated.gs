/*
══════════════════════════════════════════════════════════
  SMART STUDY — MASTER GAS (Updated)
  Script Properties:
    FIREBASE_URL     → https://yourproject-default-rtdb.firebaseio.com/
    SECRET_KEY       → ss_2024_abc123mnb  (GitHub secret এর মানের মতো হতে হবে)
    FCM_PROJECT_ID   → your-project-id
    FCM_CLIENT_EMAIL → firebase-adminsdk@...
    PRIVATE_KEY      → -----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n
    GEMINI_API_KEY   → AIza...
    ADMIN_PHONE      → 01XXXXXXXXX
══════════════════════════════════════════════════════════
*/

// 🆕 ডিপ্লয়মেন্ট-ভেরিফিকেশন মার্কার — নিচে doGet()-এ ?action=version হ্যান্ডলার
// এই ভ্যারিয়েবলটা রিটার্ন করে। কোড আপডেট করার পর "Deploy → Manage deployments →
// Edit (পেন্সিল আইকন) → Version: New version → Deploy" ঠিকভাবে করা হয়েছে কিনা
// নিশ্চিত হতে চাইলে GAS_URL-এর শেষে ?action=version জুড়ে ব্রাউজারে খুললেই এই
// build-নামটা দেখা যাবে (secret লাগবেনা) — যদি পুরনো মান দেখা যায় বা এরর আসে,
// তার মানে নতুন কোড এখনো লাইভ হয়নি (নতুন "deployment" বানানো হয়ে থাকলে সেটার
// আলাদা URL হয়, পুরনো URL-এই পুরনো কোড থেকে যায় — এই কারণেই এই মার্কার)।
var GAS_BUILD_VERSION = "2026-08-30-update_explanation-dirty-fix-v2";

function getProps() {
  var p = PropertiesService.getScriptProperties();
  return {
    FIREBASE_URL:     p.getProperty("FIREBASE_URL")     || "",
    SECRET_KEY:       p.getProperty("SECRET_KEY")       || "",
    FCM_PROJECT_ID:   p.getProperty("FCM_PROJECT_ID")   || "",
    FCM_CLIENT_EMAIL: p.getProperty("FCM_CLIENT_EMAIL") || "",
    PRIVATE_KEY:      p.getProperty("PRIVATE_KEY")      || "",
    GEMINI_API_KEY:   p.getProperty("GEMINI_API_KEY")   || "",
    ADMIN_PHONE:      p.getProperty("ADMIN_PHONE")      || "",
    FIREBASE_DB_SECRET: p.getProperty("FIREBASE_DB_SECRET") || "",
  };
}

function hashPassword(password) {
  var rawBytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, password, Utilities.Charset.UTF_8);
  return rawBytes.map(function(b){ return ('0'+(b&0xFF).toString(16)).slice(-2); }).join('');
}

// ── subject/topic/sub_topic-এর মতো লেবেল normalize করে — শুধু invisible zero-width
// char (\u200B\u200C\u200D\uFEFF, nbsp) বাদ দেয় আর extra whitespace collapse করে,
// visible টেক্সট/emoji অক্ষত রাখে। renameField-এ এটা দিয়েই ম্যাচ করা হয়, যাতে
// invisible-char-এ আলাদা কিন্তু দেখতে-একই-রকম variant একবারেই merge হয়ে যায়।
function normalizeFieldValue_(s){
  return (s===undefined||s===null?"":s.toString())
    .replace(/[\u200B\u200C\u200D\uFEFF\u00A0]/g,"")
    .replace(/\s+/g," ")
    .trim();
}

/* ══════════════════════════════════════════════════════════════════════════
   markTopicDirty(topicId) — GitHub CDN Plan (Delta-Publish)-এর ভিত্তি।
   কোনো Topic-এর প্রশ্ন এডিট/ডিলিট/মুভ/রিনেম হলে এখানে কল করে জানিয়ে দেওয়া হয় —
   একটা ছোট "_DirtyTopics" শিটে (নেই থাকলে নিজে থেকে তৈরি হয়) topic_id + সময়
   জমা থাকে। GAS Publish স্ক্রিপ্ট (পরে বানানো হবে) এই লিস্ট পড়ে **শুধু** dirty
   topic-গুলোর JSON রিজেনারেট করবে — হাজার হাজার প্রশ্ন bulk-move করলেও পুরো
   ডেটাসেট re-publish করা লাগবে না, শুধু যা বদলেছে তাই।
   ⚠️ এই ফাংশনটা ছোট, দ্রুত (কোনো নেটওয়ার্ক কল নেই) — তাই লক-করা action-গুলোর
   ভিতরেই নিরাপদে কল করা যায়, বাড়তি সময় লাগে না বললেই চলে।
   ══════════════════════════════════════════════════════════════════════════ */
/* ── reviewLabelForField — কোন ফিল্ড এডিট হলে Review কলামে কোন লেবেল যোগ হবে
   তার ম্যাপিং। শুধু "মূল প্রশ্ন/উত্তর/option/ব্যাখ্যা/subject/topic" — এই ৬
   ক্যাটাগরির এডিটই ট্র্যাক করা হয় (technique/tags/timestamp-জাতীয় মেটাডেটা
   এডিটে Review কলাম ছোঁয়া হয় না, নাহলে অপ্রয়োজনীয় শব্দে ভরে যাবে)।
   🐛 ফিক্স (কতদিন ধরে "Edit ব্যর্থ, ফিল্ড: সবগুলো" — আসল কারণ ধরাই পড়ছিল না):
   এই ফাংশনটা ভুলবশত doGet(e){...}-এর ভেতরে nested function হিসেবে সংজ্ঞায়িত
   ছিল — তাই শুধু doGet-এর ভেতর থেকেই এটা কল করা যেত। doPost-এর update_fields
   অ্যাকশন থেকে এটা কল করতে গেলেই "ReferenceError: reviewLabelForField is not
   defined" ছুঁড়ে পুরো ফাংশন ক্র্যাশ করতো — for-loop-এর প্রথম ফিল্ডটা লেখা হয়ে
   যাওয়ার ঠিক পরপরই (Sheet-এ setValue() হয়ে যাওয়ার পরই) এই ক্র্যাশ হতো, তাই
   কখনো কখনো একটা ফিল্ড আংশিক লেখা হয়েও বাকি সব ফিল্ড ছাড়াই থেমে যেত। এখন এই
   ফাংশনটা গ্লোবাল স্কোপে (ফাইলের একদম উপরে) নিয়ে আসা হলো, doGet ও doPost —
   দুটো থেকেই এখন এটা কল করা যাবে। ── */
function reviewLabelForField(fld){
  var f=(fld||"").toString().toLowerCase().trim();
  if(f==="question") return "Question Reviewed";
  if(f==="correct") return "Ans Reviewed";
  if(f==="opt1"||f==="opt2"||f==="opt3"||f==="opt4"||f==="option1"||f==="option2"||f==="option3"||f==="option4") return "Option Reviewed";
  if(f==="explanation") return "Explanation Reviewed";
  if(f==="subject"||f==="subject_id"||f==="subjectid") return "Subject Reviewed";
  if(f==="topic"||f==="sub_topic"||f==="subtopic"||f==="topic_id"||f==="topicid") return "Topic Reviewed";
  return null;
}

function markTopicDirty(topicId) {
  if (!topicId) return;
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sh = ss.getSheetByName("_DirtyTopics");
    if (!sh) {
      sh = ss.insertSheet("_DirtyTopics");
      sh.getRange(1,1,1,2).setValues([["topic_id","markedAt"]]);
    }
    var lastRow = sh.getLastRow();
    if (lastRow >= 2) {
      var ids = sh.getRange(2,1,lastRow-1,1).getValues();
      for (var i=0;i<ids.length;i++){
        if ((ids[i][0]||"").toString() === topicId) {
          sh.getRange(i+2,2).setValue(Date.now());  // আগে থেকেই আছে — শুধু টাইমস্ট্যাম্প রিফ্রেশ
          return;
        }
      }
    }
    sh.appendRow([topicId, Date.now()]);
  } catch (mtdErr) {
    // dirty-tracking ব্যর্থ হলেও আসল mutation (edit/delete/move) যেন কখনো ব্যর্থ না
    // হয় — এটা শুধু পরে publish-এর জন্য "সহায়ক তথ্য", ক্রিটিক্যাল পাথ না
    Logger.log("markTopicDirty error (non-fatal): " + mtdErr);
  }
}

/* ══════════════════════════════════════════════════════════════════════════
   একাধিক Topic একসাথে dirty মার্ক করতে — renameField/deleteByIds-এর মতো
   action-গুলোতে একসাথে অনেক distinct topicId touched হতে পারে, সেগুলো Set
   বানিয়ে একবারে পাস করার জন্য (loop এর ভিতরে বারবার markTopicDirty() কল করলে
   প্রতিবারই পুরো "_DirtyTopics" শিট আবার পড়তে হতো — অপ্রয়োজনীয়)
   ══════════════════════════════════════════════════════════════════════════ */
function markTopicsDirty(topicIdSet) {
  for (var tid in topicIdSet) { if (topicIdSet.hasOwnProperty(tid) && tid) markTopicDirty(tid); }
}

/* ══════════════════════════════════════════════════════════════════════════
   withWriteLock(fn) — কোনো Sheet-write action (updateField/deleteByIds/
   moveQuestions/moveTopic/renameField ইত্যাদি)-কে script-lock দিয়ে wrap করার
   জন্য common helper (getNextId()-এর প্যাটার্নই, শুধু reusable করা হলো)।
   Smart Study App (instant-local + background sync) আর Admin App (OCR
   bulk-add) — দুটোই একই Sheet-এ লেখে, প্রায়ই কাছাকাছি সময়ে। লক ছাড়া থাকলে:
   একটা action রো-ইনডেক্স হিসাব করে রাখার পরই আরেকটা action যদি রো শিফট করে
   ফেলে (ডিলিট/ইনসার্ট), প্রথমটা ভুল রো-তে গিয়ে লিখতে পারে। ৩০ সেকেন্ড টাইমআউট
   (getNextId()-এর ১৫ সেকেন্ডের চেয়ে একটু বেশি, কারণ moveQuestions/deleteByIds
   বড় sheet-এ কিছুটা সময় নিতে পারে)।
   ══════════════════════════════════════════════════════════════════════════ */
// ── notifyAdminPublishFailure_ — Publish ব্যর্থ হলে ADMIN_PHONE-এ FCM push
// পাঠায় (adminNotify action যেভাবে করে ঠিক সেই একই sendFCMToPhone_ ব্যবহার
// করে) — এখন এই notification পাওয়ার জন্য Publish ট্যাব খুলে বসে থাকতে হবে
// না, বিশেষ করে auto-scheduled publish (publishScheduled) ব্যর্থ হলে এটাই
// একমাত্র সংকেত। notification পাঠানো ব্যর্থ হলেও (ADMIN_PHONE সেট নেই,
// token নেই ইত্যাদি) মূল publish ফ্লো কখনো এর কারণে ভাঙবে না। ──
function notifyAdminPublishFailure_(message) {
  try {
    var adminPhone = (PropertiesService.getScriptProperties().getProperty("ADMIN_PHONE")||"").toString().replace(/^'+/,'').trim();
    if (!adminPhone) return;
    sendFCMToPhone(adminPhone, "🚨 CDN Publish ব্যর্থ!", (message||"").toString().substring(0,150), {type:"publish_failed", url:"publish"});
  } catch (notifyErr) { /* নোটিফিকেশন ব্যর্থ হলেও মূল publish ফ্লো অক্ষত থাকবে */ }
}

function withWriteLock(fn) {
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(30000);
  } catch (lockErr) {
    logError_("withWriteLock", "Lock timeout/failure: " + lockErr);
    throw lockErr; // আগের মতোই ছড়িয়ে যাবে, শুধু আগে একটা লগ থেকে যাচ্ছে
  }
  try {
    return fn();
  } finally {
    lock.releaseLock();
  }
}

/* ═══════════════════════════════════════════════════════════════════════
   PHASE 3 — GAS Publish Pipeline (Dirty-Topic Delta → GitHub → manifest.json)
   ═══════════════════════════════════════════════════════════════════════
   "Publish Now" (Admin App বাটন, doGet action="publishNow") অথবা scheduled
   trigger (scheduledPublish()) থেকে কল হয়। শুধু "_DirtyTopics" শিটে থাকা
   topic_id-গুলোর JSON রিজেনারেট করে GitHub-এ commit করে — পুরো ডেটাসেট
   re-publish করে না, তাই ২১,০০০+ প্রশ্ন bulk-move করলেও দ্রুত ও কম-quota-তে
   চলে (দেখো GAS_CDN_PLANNING.md-এর "Dirty-Topic Queue" সেকশন)।

   প্রয়োজনীয় Script Properties (Apps Script এডিটর → Project Settings →
   Script Properties-এ ম্যানুয়ালি বসাতে হবে, একবারই):
     GITHUB_WRITE_TOKEN — Contents:Read+Write স্কোপ, শুধু এই একটা repo-তে
                           (⚠️ Worker-এর token থেকে আলাদা — সেটা read-only,
                           এটা write করে, দুটো এক না রাখাই নিরাপদ)
     GH_OWNER            — GitHub username/org
     GH_REPO             — repo name (Worker-এর wrangler.toml-এর সাথে মিলিয়ে)
     GH_BRANCH           — সাধারণত "main"
   ═══════════════════════════════════════════════════════════════════════ */

function publishDirtyTopics() {
  var props = PropertiesService.getScriptProperties();
  var STALE_MS = 10 * 60 * 1000; // ১০ মিনিট — GAS-এর ৬ মিনিট hard execution
                                  // limit-এর চেয়ে বেশি safety margin রেখে।
                                  // timeout-এ মাঝপথে থেমে গেলে finally ব্লক
                                  // চলার সুযোগ নাও পেতে পারে, ফলে lock আটকে
                                  // থেকে যেতে পারে — তাই "পুরনো" lock নিজে
                                  // থেকেই stale ধরে auto-clear করা হয়, কোনো
                                  // ম্যানুয়াল unlock action ছাড়াই self-healing
  var startedAt = props.getProperty("publishStartedAt");
  if (props.getProperty("isPublishing") === "1") {
    if (startedAt && (Date.now() - parseInt(startedAt, 10)) < STALE_MS) {
      return { status: "error", result: "error", message: "ইতিমধ্যে একটা Publish চলছে, একটু পরে আবার চেষ্টা করুন" };
    }
    Logger.log("Stale publish lock detected (>10min old) — auto-clearing এবং চালিয়ে যাওয়া হচ্ছে");
  }
  props.setProperty("isPublishing", "1");
  props.setProperty("publishStartedAt", String(Date.now()));
  try {
    return doPublish_();
  } catch (pubErr) {
    Logger.log("publishDirtyTopics FATAL error: " + pubErr);
    notifyAdminPublishFailure_("Publish crash (unexpected): " + pubErr);
    return { status: "error", result: "error", message: "Publish ব্যর্থ (unexpected): " + pubErr };
  } finally {
    props.deleteProperty("isPublishing");
    props.deleteProperty("publishStartedAt");
  }
}

function doPublish_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var props = PropertiesService.getScriptProperties();
  var ghOwner = props.getProperty("GH_OWNER");
  var ghRepo = props.getProperty("GH_REPO");
  var ghBranch = props.getProperty("GH_BRANCH") || "main";
  var ghToken = props.getProperty("GITHUB_WRITE_TOKEN");
  if (!ghOwner || !ghRepo || !ghToken) {
    return { status: "error", result: "error", message: "GitHub config (GH_OWNER/GH_REPO/GITHUB_WRITE_TOKEN) Script Properties-এ সেট করা নেই" };
  }

  // ── PERMANENT FIX (২০২৬-০৮-৩০ — "Quiz sheet-এ অনেক প্রশ্ন থাকলেও app-এ শুধু
  // ১টা subject/topic দেখায়, Publish Now যতবারই করা হোক না কেন"): app-এর
  // Subject/Topic browse-tree সম্পূর্ণভাবে Topics শিটের row_count_quiz/
  // row_count_qbank/row_count_study কলামের ওপর নির্ভর করে — এই কলাম ০/ফাঁকা
  // থাকলে সেই Topic app-এ একদমই দেখা যায় না। এই কলাম আগে আপডেট হতো *শুধু*
  // runRebuildIndexCore() চললে — আর সেটা চলত শুধু ম্যানুয়ালি rebuildIndex চাপলে,
  // অথবা installAutoReindexTrigger() দিয়ে বসানো ১৫-মিনিটের ট্রিগার সক্রিয়
  // থাকলে (যেটা এতদিন ম্যানুয়ালি ইনস্টল করা লাগতো, প্রায় কখনোই করা হয়নি)।
  // "Publish Now" শুধু প্রশ্নের কনটেন্ট CDN-এ পাঠাত, row_count কখনো ছুঁতোই না।
  // এখন Publish Now শুরুতেই runRebuildIndexCore() কল করে — তাই admin যেই
  // action বারবার চাপে, সেটাই এখন থেকে সবসময় সঙ্গে সঙ্গে পুরো index রিফ্রেশ
  // করে দেয়, আলাদা ম্যানুয়াল ট্রিগার সেটআপের ওপর আর নির্ভর করতে হয় না। এটা
  // batch/in-memory-buffered (দেখো runRebuildIndexCore()), তাই বড় ডেটাসেটেও
  // দ্রুত। reindex ব্যর্থ হলেও মূল publish ফ্লো আটকায় না — লগ রাখা হয়, পরের
  // Publish Now-এই আবার চেষ্টা হবে। ──
  try {
    runRebuildIndexCore();
  } catch (ribErr) {
    logError_("doPublish_/runRebuildIndexCore", String(ribErr));
  }

  var dirtySh = ss.getSheetByName("_DirtyTopics");
  if (!dirtySh || dirtySh.getLastRow() < 2) {
    return { status: "success", result: "success", message: "কোনো dirty topic নেই, publish করার কিছু নেই", published: 0, failed: 0 };
  }

  var dirtyRows = dirtySh.getRange(2, 1, dirtySh.getLastRow() - 1, 1).getValues();
  var uniqTopicIds = {};
  dirtyRows.forEach(function (r) { var t = (r[0] || "").toString(); if (t) uniqTopicIds[t] = 1; });
  var allDirtyTopicIds = Object.keys(uniqTopicIds);
  if (!allDirtyTopicIds.length) {
    return { status: "success", result: "success", message: "কোনো dirty topic নেই", published: 0, failed: 0 };
  }

  // ── Safety cap: এক Publish-এ সর্বোচ্চ এতগুলো টপিক (GAS-এর ৬ মিনিট hard
  // execution limit-এ যেন কখনোই ধাক্কা না লাগে, ২১,০০০+ প্রশ্ন bulk-reclassify-
  // এর মতো কাজেও)। বাকিগুলো dirty list-এই থেকে যায় (clearContent()-এ শুধু
  // processed অংশ সরানো হবে নিচে), পরের Publish-এ (ম্যানুয়াল আবার চাপলে বা
  // scheduled trigger-এ) বাকিটা এগোবে — বড় bulk কাজে একাধিকবার Publish লাগতে
  // পারে, সেটাই প্রত্যাশিত ও নিরাপদ। ──
  var MAX_TOPICS_PER_RUN = 400;
  var dirtyTopicIds = allDirtyTopicIds.slice(0, MAX_TOPICS_PER_RUN);
  var remainingAfterThisRun = allDirtyTopicIds.length - dirtyTopicIds.length;

  // ── Topics/Subjects reference-ডেটা লোড (নাম resolve করতে, JSON ফাইলে
  // subject/subTopic-এর মানুষ-পড়ার-মতো নাম বসানোর জন্য) ──
  var topicsSh = ss.getSheetByName("Topics");
  var topicsMap = {};
  if (topicsSh) {
    var tData = topicsSh.getDataRange().getValues(), tHdr = tData[0];
    // 🐛 FIX (২০২৬-০৮-২৯): আগে এখানে tHdr.indexOf("name") ছিল — Topics শিটে
    // "name" নামে কোনো কলামই নেই (আসল কলাম নাম "topic_name"), তাই indexOf
    // সবসময় -1 রিটার্ন করত, ফলে topicsMap-এর প্রতিটা এন্ট্রির .name সবসময়
    // undefined হয়ে যেত। এর প্রভাবে manifest.topics[].subTopic সবসময় বাদ
    // পড়ত, আর প্রতিটা প্রশ্নের q["topic"] ফিল্ডে জোর করে undefined বসে
    // JSON.stringify-এ সেই key-ই হারিয়ে যেত (Quiz/QBank-এ "no topic" বাগ)।
    var tIdCol = tHdr.indexOf("topic_id"), tNameCol = tHdr.indexOf("topic_name"), tSubjIdCol = tHdr.indexOf("subject_id");
    for (var ti = 1; ti < tData.length; ti++) {
      var tid = (tData[ti][tIdCol] || "").toString();
      if (tid) topicsMap[tid] = { name: tData[ti][tNameCol], subjectId: (tData[ti][tSubjIdCol] || "").toString() };
    }
  }
  var subjectsSh = ss.getSheetByName("Subjects");
  var subjectsMap = {};
  if (subjectsSh) {
    var sData = subjectsSh.getDataRange().getValues(), sHdr = sData[0];
    // 🐛 FIX (২০২৬-০৮-২৯): একই বাগ — Subjects শিটের আসল কলাম নাম "subject_name",
    // "name" না। আগে এটা ভুল থাকায় subjectsMap-এর সব value undefined হতো,
    // manifest.topics[].subject সবসময় "" (খালি) থেকে যেত।
    var sIdCol = sHdr.indexOf("subject_id"), sNameCol = sHdr.indexOf("subject_name");
    for (var si = 1; si < sData.length; si++) {
      var sid = (sData[si][sIdCol] || "").toString();
      if (sid) subjectsMap[sid] = sData[si][sNameCol];
    }
  }

  // ── বর্তমান manifest.json আনা (existing topics-এর hash/count বজায় রাখতে —
  // শুধু dirty topic-গুলোর এন্ট্রি আপডেট হবে, বাকিগুলো অক্ষত থাকবে) ──
  var manifestGet = ghGetFile_(ghOwner, ghRepo, ghBranch, "manifest.json", ghToken);
  var manifest = manifestGet.exists ? JSON.parse(manifestGet.content) : { version: 0, schemaVersion: 1, topics: {} };
  if (!manifest.topics) manifest.topics = {};

  // ── পুরো repo-র file→sha ম্যাপ একবারেই আনা (ghGetTree_) — প্রতিটা টপিক-
  // ফাইলের জন্য আলাদা GET কল এড়ানোর জন্য (বাল্ক publish-এ GitHub API কল
  // প্রায় অর্ধেক করে দেয়, ব্যর্থ হলে খালি {} আসে, তখন per-file lookup-এ
  // স্বয়ংক্রিয়ভাবে fallback হয়) ──
  var shaMap = ghGetTree_(ghOwner, ghRepo, ghBranch, ghToken);

  var results = { published: 0, failed: 0, errors: [], totalQuestions: 0 };

  // 🐛 EFFICIENCY FIX (২১,০০০+ প্রশ্ন bulk-reclassify প্রজেক্টের জন্য জরুরি):
  // আগে প্রতিটা dirty topic-এর জন্য আলাদাভাবে dataSh.getDataRange().getValues()
  // কল হতো (পুরো Quiz/QBank/Study শিট রিড+স্ক্যান) — মানে ৫০০টা dirty topic হলে
  // একই বড় শিট ৫০০ বার রিড হতো! GAS-এর ৬ মিনিট hard execution limit-এ এটা
  // সহজেই ধাক্কা খেত বড় bulk-move-এর পরে। এখন প্রতিটা sheet (Quiz/QBank/Study)
  // **একবারই** পড়া হয়, single pass-এ সব dirty topic_id-এর রো একসাথে গ্রুপ করে
  // নেওয়া হয় — তারপর প্রতিটা topic শুধু তার নিজের already-grouped রো-গুলো থেকে
  // JSON বানায় (কোনো re-scan নেই)।
  var dirtyBySheet = { Quiz: [], QBank: [], Study: [] };
  dirtyTopicIds.forEach(function (tid) {
    var sn = tid.indexOf("QZ") === 0 ? "Quiz" : tid.indexOf("QB") === 0 ? "QBank" : tid.indexOf("ST") === 0 ? "Study" : null;
    if (sn) dirtyBySheet[sn].push(tid);
    else { results.errors.push(tid + ": অজানা sheet prefix"); results.failed++; }
  });

  ["Quiz", "QBank", "Study"].forEach(function (sheetName) {
    var sheetDirtyIds = dirtyBySheet[sheetName];
    if (!sheetDirtyIds.length) return;

    var dataSh = ss.getSheetByName(sheetName);
    if (!dataSh) {
      sheetDirtyIds.forEach(function (tid) { results.errors.push(tid + ": " + sheetName + " sheet পাওয়া যায়নি"); results.failed++; });
      return;
    }
    var data = dataSh.getDataRange().getValues(), hdr = data[0];
    var topicIdCol = hdr.indexOf("topic_id");
    if (topicIdCol < 0) {
      sheetDirtyIds.forEach(function (tid) { results.errors.push(tid + ": " + sheetName + "-এ topic_id কলাম নেই"); results.failed++; });
      return;
    }
    var subTopicKey = hdr.indexOf("sub_topic") >= 0 ? "sub_topic" : (hdr.indexOf("topic") >= 0 ? "topic" : null);

    // ── single pass — শুধু dirty topic_id-গুলোর রো গ্রুপ করে নেওয়া হচ্ছে ──
    var dirtySet = {};
    sheetDirtyIds.forEach(function (tid) { dirtySet[tid] = true; });
    var rowsByTopic = {}; // topicId -> [qObj, qObj, ...]
    for (var ri = 1; ri < data.length; ri++) {
      var rowTopicId = (data[ri][topicIdCol] || "").toString();
      if (!dirtySet[rowTopicId]) continue;
      var qObj = {};
      for (var ci = 0; ci < hdr.length; ci++) {
        var key = hdr[ci];
        if (!key) continue;
        var val = data[ri][ci];
        qObj[key] = (val instanceof Date) ? val.getTime() : val;
      }
      (rowsByTopic[rowTopicId] || (rowsByTopic[rowTopicId] = [])).push(qObj);
    }

    // ── এখন প্রতিটা dirty topic — pre-grouped রো থেকেই (কোনো re-scan ছাড়াই) ──
    sheetDirtyIds.forEach(function (topicId) {
      try {
        var topicMeta = topicsMap[topicId];
        var resolvedSubject = topicMeta && subjectsMap[topicMeta.subjectId] ? subjectsMap[topicMeta.subjectId] : null;
        var resolvedSubTopic = topicMeta ? topicMeta.name : null;

        var questions = rowsByTopic[topicId] || [];
        if (resolvedSubject !== null || (resolvedSubTopic !== null && subTopicKey)) {
          questions.forEach(function (q) {
            if (resolvedSubject !== null) q["subject"] = resolvedSubject;
            if (resolvedSubTopic !== null && subTopicKey) q[subTopicKey] = resolvedSubTopic;
          });
        }

        var filePath = sheetLowerName_(sheetName) + "/" + topicId + ".json";
        var knownSha = shaMap.hasOwnProperty(filePath) ? shaMap[filePath] : null;

        if (questions.length === 0) {
          // ── এই টপিকে আর কোনো প্রশ্ন নেই (সব move/delete হয়ে গেছে) — GitHub-এ
          // ফাইলটা থাকলে মুছে দেওয়া হচ্ছে, manifest থেকেও এন্ট্রি সরানো হচ্ছে ──
          ghDeleteFile_(ghOwner, ghRepo, ghBranch, filePath, ghToken, knownSha);
          delete manifest.topics[topicId];
          results.published++;
          return;
        }

        var jsonStr = JSON.stringify(questions);
        var hash = computeHash_(jsonStr);
        var putResult = ghPutFile_(ghOwner, ghRepo, ghBranch, filePath, jsonStr, ghToken,
          "Publish " + topicId + " (" + questions.length + " questions)", knownSha);
        if (!putResult.success) {
          results.errors.push(topicId + ": GitHub commit ব্যর্থ — " + putResult.error);
          logError_("publishDirtyTopics/ghPutFile_", topicId + ": " + putResult.error);
          results.failed++;
          return;
        }

        var subjectName = topicMeta && subjectsMap[topicMeta.subjectId] ? subjectsMap[topicMeta.subjectId] : "";
        var topicName = topicMeta ? topicMeta.name : "";
        manifest.topics[topicId] = { subject: subjectName, subTopic: topicName, count: questions.length, hash: hash };
        results.totalQuestions += questions.length;
        results.published++;

      } catch (topicErr) {
        results.errors.push(topicId + ": " + topicErr);
        logError_("publishDirtyTopics/topicErr", topicId + ": " + topicErr);
        results.failed++;
      }
    });
  });

  if (results.failed > 0) {
    notifyAdminPublishFailure_(results.failed + "টা Topic publish হতে ব্যর্থ হয়েছে (মোট " + results.published + "টা সফল)। বিস্তারিত _SystemLogs শিটে।");
  }

  // ── Reference ডেটা — প্রতিবার publish-এ রিফ্রেশ, এটা ছোট ডেটা বলে আলাদা
  // dirty-tracking না করে সবসময় আপডেট করাই সহজ।
  // FIX (Speed Plan Task 2): আগে শুধু subjects.json/topics.json publish হতো।
  // App-এর read path পুরোপুরি CDN-only করার প্ল্যানে QBank-এর পদবী/প্রতিষ্ঠান/
  // সাল-ভিত্তিক ব্রাউজ ফিচারও (এখন পর্যন্ত partial-Room-sync-নির্ভর) CDN থেকে
  // চলার কথা — তাই Tags/Posts/Institutions/Exam_Appearances ও এখন publish হচ্ছে।
  // (Headings শিট শুধু admin-এর নিজের documentation, CDN-এ দরকার নেই বলে বাদ।) ──
  if (results.published > 0) {
    // 🔬 DIAG (২০২৬-০৮-২৯ — reference/*.json বার বার GitHub-এ commit না হওয়ার
    // আসল কারণ ধরার জন্য): আগে subjects.json থেকে exam-appearances.json
    // পর্যন্ত সবগুলো ফাইল একটাই try/catch-এ মোড়ানো ছিল — প্রথম ফাইলেই
    // (subjects.json) ব্যর্থ হলে বাকি সবগুলো (topics/tags/posts/institutions/
    // exam-appearances) কখনো চেষ্টাই হতো না, আর error শুধু Logger.log-এ যেত
    // (কোথাও দৃশ্যমান না)। এখন প্রতিটা ফাইল আলাদা try/catch-এ, একটা ব্যর্থ
    // হলেও বাকিগুলো চলতেই থাকবে — আর প্রতিটার ফলাফল (success/error/rows/bytes)
    // একটা Script Property-তে জমা হয়, GAS_URL+"?action=refDiag" খুলে
    // ব্রাউজারেই সরাসরি দেখা যাবে। রুট কারণ পাওয়া গেলে এই diagnostic কোড আর
    // refDiag action ফেরত মুছে ফেলা উচিত। ──
    var refDiag = { at: new Date().toString(), files: {} };

    var refPublishOne_ = function (label, sheetName, filePath, commitMsg) {
      var sh = ss.getSheetByName(sheetName);
      if (!sh) {
        refDiag.files[label] = { skipped: true, reason: "sheet '" + sheetName + "' not found" };
        return;
      }
      try {
        var data = sheetToJsonArray_(sh);
        var jsonStr = JSON.stringify(data);
        var result = ghPutFile_(ghOwner, ghRepo, ghBranch, filePath, jsonStr, ghToken, commitMsg);
        refDiag.files[label] = { rows: data.length, bytes: jsonStr.length, result: result };
      } catch (err) {
        refDiag.files[label] = { success: false, error: "EXCEPTION: " + err };
        logError_("refPublishOne_/" + label, String(err));
      }
    };

    refPublishOne_("subjects", "Subjects", "reference/subjects.json", "Update reference/subjects.json");
    refPublishOne_("topics", "Topics", "reference/topics.json", "Update reference/topics.json");
    refPublishOne_("tags", "Tags", "reference/tags.json", "Update reference/tags.json");
    refPublishOne_("posts", "Posts", "reference/posts.json", "Update reference/posts.json");
    refPublishOne_("institutions", "Institutions", "reference/institutions.json", "Update reference/institutions.json");
    refPublishOne_("examAppearances", "Exam_Appearances", "exam-appearances.json", "Update exam-appearances.json");

    try {
      PropertiesService.getScriptProperties().setProperty("LAST_REFERENCE_DIAG", JSON.stringify(refDiag));
    } catch (propErr) {
      Logger.log("reference diag property write failed: " + propErr);
    }
    Logger.log("reference publish diag: " + JSON.stringify(refDiag));
  }

  // ── Sanity-check: Sheet-এর আসল মোট প্রশ্নসংখ্যা vs manifest-এ থাকা মোট
  // count — অমিল থাকলে warning (publish আটকায় না, কিন্তু চোখে পড়ার মতো করে
  // ফলাফলে ফেরত যায়) ──
  var totalInSheets = countAllQuestions_(ss);
  var totalInManifest = 0;
  for (var mtid in manifest.topics) { if (manifest.topics.hasOwnProperty(mtid)) totalInManifest += (manifest.topics[mtid].count || 0); }
  var sanityWarning = null;
  if (totalInSheets !== totalInManifest) {
    sanityWarning = "⚠️ Sheet-এ মোট " + totalInSheets + "টি প্রশ্ন, কিন্তু manifest-এ মোট " + totalInManifest +
      "টি — অমিল থাকতে পারে কোনো টপিক এখনো কখনো publish হয়নি বলে (স্বাভাবিক, প্রথমবার সব dirty মার্ক করলে ঠিক হয়ে যাবে), অথবা কোনো bug-এর ইঙ্গিত।";
  }

  // ── FIX (Speed Plan Task 2): প্রতিটা subject-এর মোট প্রশ্নসংখ্যা manifest-এই
  // আগে থেকে যোগ করে রাখা হচ্ছে (topicsMap দিয়ে প্রতিটা topic কোন subject-এর,
  // সেটা রিজলভ করে count যোগ করে) — যাতে App-এ Subject list খোলার সময় প্রতিটা
  // topic আলাদা করে ডাউনলোড না করেই, শুধু manifest থেকেই সঠিক "মোট প্রশ্ন"
  // instant দেখানো যায় (আগে এই সংখ্যা locally hardcoded 0 রাখা হতো)। ──
  var subjectTotals = {};
  for (var stid in manifest.topics) {
    if (!manifest.topics.hasOwnProperty(stid)) continue;
    var stMeta = topicsMap[stid];
    var stSubjectId = stMeta ? stMeta.subjectId : null;
    if (!stSubjectId) continue;
    subjectTotals[stSubjectId] = (subjectTotals[stSubjectId] || 0) + (manifest.topics[stid].count || 0);
  }
  manifest.subjectTotals = subjectTotals;

  // ── manifest.json commit (অন্তত ১টা সফল হলেই) ──
  if (results.published > 0) {
    manifest.version = (manifest.version || 0) + 1;
    manifest.publishedAt = Date.now();
    var manifestPut = ghPutFile_(ghOwner, ghRepo, ghBranch, "manifest.json", JSON.stringify(manifest), ghToken, "Update manifest (v" + manifest.version + ")");
    if (!manifestPut.success) {
      logError_("publishDirtyTopics/manifestCommit", "manifest.json commit ব্যর্থ: " + manifestPut.error + " (topics published: " + results.published + ")");
      notifyAdminPublishFailure_("manifest.json commit ব্যর্থ (topics published: " + results.published + "): " + manifestPut.error);
      return { status: "error", result: "error", message: "Topic ফাইল publish হলেও manifest.json commit ব্যর্থ: " + manifestPut.error, published: results.published, failed: results.failed };
    }
  }

  // ── dirty list ক্লিয়ার — শুধু এই রানে processed (dirtyTopicIds) টপিকগুলোই
  // সরানো হয়, cap-এর কারণে বাদ পড়া বা এই ফাংশন চলাকালীন নতুন যোগ হওয়া কোনো
  // dirty entry অক্ষত থাকে। "সিদ্ধান্ত ক" অনুযায়ী: কোনো ব্যর্থতা থাকলে
  // (failed>0) পুরো _DirtyTopics টেবিলই অক্ষত রাখা হয় (সফলগুলোও রিপাবলিশ
  // হতে পারে পরের বার, ক্ষতি নেই — retry নিরাপদ) — শুধু সব-সফল হলেই এই রানের
  // processed অংশ মোছা হয়। ──
  if (results.failed === 0) {
    var processedSet = {};
    dirtyTopicIds.forEach(function (t) { processedSet[t] = true; });
    var currentDirtyData = dirtySh.getLastRow() >= 2 ? dirtySh.getRange(2, 1, dirtySh.getLastRow() - 1, 2).getValues() : [];
    var remainingRows = currentDirtyData.filter(function (row) { return !processedSet[(row[0] || "").toString()]; });
    dirtySh.getRange(2, 1, Math.max(1, dirtySh.getLastRow() - 1), 2).clearContent();
    if (remainingRows.length) {
      dirtySh.getRange(2, 1, remainingRows.length, 2).setValues(remainingRows);
    }
  }

  return {
    status: results.failed === 0 ? "success" : "partial",
    result: results.failed === 0 ? "success" : "partial",
    published: results.published,
    failed: results.failed,
    errors: results.errors,
    totalQuestions: results.totalQuestions,
    manifestVersion: manifest.version,
    sanityWarning: sanityWarning,
    // ── MAX_TOPICS_PER_RUN cap-এর কারণে এই রানে ধরা পড়েনি এমন dirty topic
    // থাকলে — Admin App UI-কে জানানো, যাতে "আরও Publish লাগবে" বোঝানো যায় ──
    remaining: remainingAfterThisRun
  };
}

/** সময়-ভিত্তিক trigger থেকে কল করার জন্য — publishDirtyTopics() নিজে থেকেই
 *  কিছুই করে না যদি _DirtyTopics খালি থাকে (সস্তায় সাথে সাথে বেরিয়ে যায়),
 *  তাই ঘনঘন চালানো নিরাপদ। "Publish Now" বাটন ছাড়াও এটাই মূল mechanism —
 *  edit করার পর কয়েক মিনিটের মধ্যেই CDN আপডেট হয়ে যায়, ম্যানুয়ালি Publish Now
 *  চাপার দরকার পড়ে না। */
function publishScheduled() {
  var result = publishDirtyTopics();
  Logger.log("publishScheduled result: " + JSON.stringify(result));
  return result;
}

// ── FIX (Speed Plan Task 2): আগে এই trigger বসাতে Apps Script এডিটরে গিয়ে
// Triggers ট্যাব থেকে ম্যানুয়ালি "Add Trigger → publishScheduled → Day timer"
// (দিনে ১ বার) সেট করতে হতো — GAS/CDN read-only আর্কিটেকচারে এত বড় গ্যাপ
// (edit করার পর সারাদিন CDN স্টেল থাকতে পারে) মেনে নেওয়া যায় না। এখন
// installAutoReindexTrigger()-এর মতোই একটা self-installing trigger — একবার
// রান করলেই প্রতি ১০ মিনিটে (দিনে ~১৪৪ বার, dirty না থাকলে প্রতিটা রান প্রায়
// বিনামূল্যে/সস্তা) নিজে থেকে চেক করে publish করবে।
//
// ⚠️ ONE-TIME SETUP: Apps Script এডিটরে এই ফাইল খুলে, ফাংশন ড্রপডাউন থেকে
// "installAutoPublishTrigger" বেছে নিয়ে ▶ Run বাটনে একবার ক্লিক করো (প্রথমবার
// authorization চাইতে পারে, allow করে দিও)। দ্বিতীয়বার রান করলে আগের trigger
// মুছে নতুন বসায় — ডুপ্লিকেট জমবে না। ──
function installAutoPublishTrigger() {
  var triggers = ScriptApp.getProjectTriggers();
  for (var i = 0; i < triggers.length; i++) {
    if (triggers[i].getHandlerFunction() === "publishScheduled") ScriptApp.deleteTrigger(triggers[i]);
  }
  ScriptApp.newTrigger("publishScheduled").timeBased().everyMinutes(10).create();
  Logger.log("✅ Auto-publish trigger installed — প্রতি ১০ মিনিটে চেক করবে, dirty topic থাকলেই CDN-এ publish হবে।");
}

/* ── GitHub Contents API helpers (write path, GAS UrlFetchApp দিয়ে) ── */

// ── logError_ — Logger.log() ব্রাউজার/এক্সিকিউশন বন্ধ হলেই হারিয়ে যায়, তাই
// ক্রিটিক্যাল এরর (GitHub publish ব্যর্থতা, lock timeout ইত্যাদি) একটা
// স্থায়ী "_SystemLogs" শিটে জমা রাখা হচ্ছে — sheet না থাকলে প্রথমবার কল হলেই
// নিজে থেকে তৈরি হয়ে যায়। লগিং নিজেই ব্যর্থ হলেও (quota/permission ইত্যাদি)
// চুপচাপ ignore হয় — মূল ফ্লো কখনো এই কারণে ভাঙবে না। ──
function logError_(context, message) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sh = ss.getSheetByName("_SystemLogs");
    if (!sh) {
      sh = ss.insertSheet("_SystemLogs");
      sh.appendRow(["timestamp", "context", "message"]);
    }
    sh.appendRow([new Date().toLocaleString('bn-BD'), context, (message||"").toString().substring(0, 500)]);
  } catch (logErr) { /* logging ব্যর্থ হলেও মূল ফ্লো অক্ষত থাকবে */ }
}

// ── fetchWithRetry_ — GitHub API মাঝেমধ্যে rate-limit (403/429) বা সাময়িক
// সার্ভার সমস্যা (502/503/504) দিতে পারে, যেটা সাথে সাথে আবার চেষ্টা করলেই
// প্রায়ই ঠিক হয়ে যায়। শুধু এই transient কোডগুলোতেই retry হয় (১s, ২s, ৩s
// ব্যাকঅফ) — 404 (ইচ্ছাকৃতভাবে "ফাইল নেই" বোঝাতে ব্যবহার হয়, ghGetFile_ দেখো)
// বা অন্য client error (400/401/422) রিট্রাই করা হয় না, কারণ বারবার একই
// ভুলই হবে। নেটওয়ার্ক এক্সসেপশন হলেও শেষ চেষ্টায় ব্যর্থ হলে exception-ই
// ছড়িয়ে যায় (কল করা কোড আগের মতোই catch করে)। ──
function fetchWithRetry_(url, options, maxRetries) {
  var retries = maxRetries || 3;
  var lastResp = null;
  for (var i = 0; i < retries; i++) {
    try {
      var resp = UrlFetchApp.fetch(url, options);
      var code = resp.getResponseCode();
      if (code < 400 || code === 404) return resp;
      if ([403, 429, 502, 503, 504].indexOf(code) === -1) return resp; // অন্য client error রিট্রাই করে লাভ নেই
      lastResp = resp;
    } catch (e) {
      if (i === retries - 1) throw e;
    }
    if (i < retries - 1) Utilities.sleep(1000 * (i + 1));
  }
  return lastResp;
}

function sheetLowerName_(sheetName) {
  return sheetName === "Quiz" ? "quiz" : sheetName === "QBank" ? "qbank" : sheetName === "Study" ? "study" : sheetName.toLowerCase();
}

// ── ghListFileCommits_ — একটা নির্দিষ্ট ফাইলের (এখানে manifest.json) সাম্প্রতিক
// commit history আনে — Rollback ফিচারের জন্য "কোন কোন পুরনো ভার্সনে ফেরা যায়"
// তার তালিকা বানাতে ব্যবহার হয়। প্রতিটা commit-এর sha দিয়েই ghGetFile_() কল
// করলে ঠিক ওই মুহূর্তের ফাইল-কনটেন্ট পাওয়া যায় (GitHub Contents API-তে
// branch-এর জায়গায় commit sha-ও ref হিসেবে দেওয়া যায়)। ──
function ghListFileCommits_(owner, repo, branch, path, token, limit) {
  var url = "https://api.github.com/repos/" + owner + "/" + repo + "/commits?path=" + encodeURIComponent(path) + "&sha=" + branch + "&per_page=" + (limit||15);
  var resp = fetchWithRetry_(url, {
    method: "get",
    headers: {
      "Authorization": "Bearer " + token,
      "Accept": "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28"
    },
    muteHttpExceptions: true
  });
  var code = resp.getResponseCode();
  if (code !== 200) return { success: false, error: "HTTP " + code + ": " + resp.getContentText() };
  var commits = JSON.parse(resp.getContentText());
  return { success: true, commits: commits.map(function(c){
    return { sha: c.sha, date: c.commit && c.commit.author ? c.commit.author.date : "", message: c.commit ? c.commit.message : "" };
  })};
}

function ghGetFile_(owner, repo, branch, path, token) {
  var url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + branch;
  var resp = fetchWithRetry_(url, {
    method: "get",
    headers: {
      "Authorization": "Bearer " + token,
      "Accept": "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28"
    },
    muteHttpExceptions: true
  });
  var code = resp.getResponseCode();
  if (code === 404) return { exists: false };
  if (code !== 200) return { exists: false, error: "HTTP " + code + ": " + resp.getContentText() };
  var body = JSON.parse(resp.getContentText());
  var content = Utilities.newBlob(Utilities.base64Decode(body.content.replace(/\n/g, ""))).getDataAsString("UTF-8");
  return { exists: true, sha: body.sha, content: content };
}

function ghPutFile_(owner, repo, branch, path, contentStr, token, message, knownSha) {
  // ── existing ফাইলের sha লাগবে update করতে। knownSha দেওয়া থাকলে (batch
  // tree lookup থেকে, দেখো ghGetTree_) আলাদা GET কল স্কিপ হয় — বাল্ক publish-এ
  // (২১,০০০+ প্রশ্ন reclassify-এর মতো কাজে) এটা GitHub API কল অর্ধেক করে দেয়,
  // যা GAS-এর ৬ মিনিট execution limit-এ ধাক্কা খাওয়ার ঝুঁকি অনেকটাই কমায়। ──
  var sha = knownSha;
  if (sha === undefined) {
    var existing = ghGetFile_(owner, repo, branch, path, token);
    sha = existing.exists ? existing.sha : null;
  }
  var url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path;
  var payload = {
    message: message || ("Update " + path),
    content: Utilities.base64Encode(contentStr, Utilities.Charset.UTF_8),
    branch: branch
  };
  if (sha) payload.sha = sha;

  var resp = fetchWithRetry_(url, {
    method: "put",
    contentType: "application/json",
    headers: {
      "Authorization": "Bearer " + token,
      "Accept": "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28"
    },
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  });
  var code = resp.getResponseCode();
  if (code === 200 || code === 201) return { success: true };
  return { success: false, error: "HTTP " + code + ": " + resp.getContentText() };
}

function ghDeleteFile_(owner, repo, branch, path, token, knownSha) {
  var sha = knownSha;
  if (sha === undefined) {
    var existing = ghGetFile_(owner, repo, branch, path, token);
    if (!existing.exists) return { success: true }; // আগে থেকেই নেই, কাজ শেষ
    sha = existing.sha;
  } else if (!sha) {
    return { success: true }; // knownSha explicitly null/falsy — tree-তেই ছিল না, মানে আগে থেকেই নেই
  }
  var url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path;
  var resp = fetchWithRetry_(url, {
    method: "delete",
    contentType: "application/json",
    headers: {
      "Authorization": "Bearer " + token,
      "Accept": "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28"
    },
    payload: JSON.stringify({ message: "Delete " + path, sha: sha, branch: branch }),
    muteHttpExceptions: true
  });
  return { success: resp.getResponseCode() === 200 };
}

/** পুরো repo-র (recursive) file→sha ম্যাপ **একটা** কলে আনে (Git Trees API) —
 *  বাল্ক publish-এ প্রতিটা ফাইলের জন্য আলাদা ghGetFile_ কল এড়ানোর জন্য।
 *  ব্যর্থ হলে খালি {} রিটার্ন করে — কল করা কোড তখন per-file lookup-এ
 *  স্বয়ংক্রিয়ভাবে fallback করে (ghPutFile_-এ knownSha=undefined মানেই সেটা)। */
function ghGetTree_(owner, repo, branch, token) {
  var url = "https://api.github.com/repos/" + owner + "/" + repo + "/git/trees/" + branch + "?recursive=1";
  var resp = fetchWithRetry_(url, {
    method: "get",
    headers: {
      "Authorization": "Bearer " + token,
      "Accept": "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28"
    },
    muteHttpExceptions: true
  });
  if (resp.getResponseCode() !== 200) return {};
  try {
    var body = JSON.parse(resp.getContentText());
    var map = {};
    (body.tree || []).forEach(function (item) {
      if (item.type === "blob") map[item.path] = item.sha;
    });
    return map;
  } catch (e) {
    return {};
  }
}

function computeHash_(str) {
  var digest = Utilities.computeDigest(Utilities.DigestAlgorithm.MD5, str, Utilities.Charset.UTF_8);
  return digest.map(function (b) { var v = (b < 0 ? b + 256 : b).toString(16); return v.length === 1 ? "0" + v : v; }).join("").substring(0, 12);
}

function countAllQuestions_(ss) {
  var total = 0;
  ["Quiz", "QBank", "Study"].forEach(function (name) {
    var sh = ss.getSheetByName(name);
    if (sh) total += Math.max(0, sh.getLastRow() - 1);
  });
  return total;
}

function sheetToJsonArray_(sh) {
  if (!sh) return [];
  var data = sh.getDataRange().getValues(), hdr = data[0];
  var out = [];
  for (var i = 1; i < data.length; i++) {
    var obj = {};
    for (var c = 0; c < hdr.length; c++) { if (hdr[c]) obj[hdr[c]] = (data[i][c] instanceof Date) ? data[i][c].getTime() : data[i][c]; }
    out.push(obj);
  }
  return out;
}

/* ══ FCM V1 ══ */
function getFCMAccessToken() {
  var cfg = getProps();
  var privateKey = cfg.PRIVATE_KEY.replace(/\\n/g, '\n');
  var now = Math.floor(Date.now()/1000);
  var header = Utilities.base64EncodeWebSafe(JSON.stringify({alg:"RS256",typ:"JWT"}));
  var claim  = Utilities.base64EncodeWebSafe(JSON.stringify({iss:cfg.FCM_CLIENT_EMAIL,scope:"https://www.googleapis.com/auth/firebase.messaging",aud:"https://oauth2.googleapis.com/token",exp:now+3600,iat:now}));
  var sig = Utilities.base64EncodeWebSafe(Utilities.computeRsaSha256Signature(header+"."+claim, privateKey));
  var jwt = header+"."+claim+"."+sig;
  var r = UrlFetchApp.fetch("https://oauth2.googleapis.com/token",{method:"post",contentType:"application/x-www-form-urlencoded",payload:"grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion="+jwt,muteHttpExceptions:true});
  return JSON.parse(r.getContentText()).access_token;
}

function sendFCMToToken(fcmToken, title, body, data) {
  try {
    var cfg = getProps();
    var accessToken = getFCMAccessToken();
    var extraData = data || {};
    extraData.title = title; extraData.body = body;
    var message = {message:{token:fcmToken,data:extraData,android:{priority:"high"}}};
    var resp = UrlFetchApp.fetch("https://fcm.googleapis.com/v1/projects/"+cfg.FCM_PROJECT_ID+"/messages:send",{method:"post",contentType:"application/json",headers:{"Authorization":"Bearer "+accessToken},payload:JSON.stringify(message),muteHttpExceptions:true});
    return JSON.parse(resp.getContentText());
  } catch(e) { return {error:e.toString()}; }
}

function getFCMTokenByPhone(phone) {
  try {
    var cfg = getProps();
    var dbSecret = PropertiesService.getScriptProperties().getProperty("FIREBASE_DB_SECRET") || cfg.SECRET_KEY;
    var safePhone = phone.toString().trim().replace(/[.#$\[\]\s]/g,'_');
    // Try users/{phone}/fcmToken first (actual path in Firebase)
    var resp1 = UrlFetchApp.fetch(cfg.FIREBASE_URL+"users/"+safePhone+"/fcmToken.json?auth="+dbSecret,{muteHttpExceptions:true});
    var t1 = JSON.parse(resp1.getContentText());
    if(t1 && typeof t1==="string" && t1.length>10) return t1;
    // Also try Users/{phone}/fcmToken (capital U)
    var resp2 = UrlFetchApp.fetch(cfg.FIREBASE_URL+"Users/"+safePhone+"/fcmToken.json?auth="+dbSecret,{muteHttpExceptions:true});
    var t2 = JSON.parse(resp2.getContentText());
    if(t2 && typeof t2==="string" && t2.length>10) return t2;
    // Fallback: old FCMTokens path
    var resp3 = UrlFetchApp.fetch(cfg.FIREBASE_URL+"FCMTokens/"+safePhone+".json?auth="+dbSecret,{muteHttpExceptions:true});
    var data = JSON.parse(resp3.getContentText());
    return (data&&data.token) ? data.token : null;
  } catch(e) { return null; }
}

function sendFCMToPhone(phone, title, body, extraData) {
  var token = getFCMTokenByPhone(phone);
  if (!token) return {error:"Token not found for "+phone};
  return sendFCMToToken(token, title, body, extraData||{});
}

function sendFCMToAll(title, body, extraData) {
  try {
    var cfg = getProps();
    var dbSecret = PropertiesService.getScriptProperties().getProperty("FIREBASE_DB_SECRET") || cfg.SECRET_KEY;
    var sent=0, failed=0;
    // Read from users path (where fcmToken is stored)
    var resp = UrlFetchApp.fetch(cfg.FIREBASE_URL+"users.json?auth="+dbSecret,{muteHttpExceptions:true});
    var users = JSON.parse(resp.getContentText());
    if(users && typeof users==='object'){
      Object.keys(users).forEach(function(phone){
        var token = users[phone] && users[phone].fcmToken;
        if(token && typeof token==="string" && token.length>10){
          var r=sendFCMToToken(token,title,body,extraData||{});
          if(r.error)failed++;else sent++;
          Utilities.sleep(80);
        }
      });
    }
    // Also try old FCMTokens path as fallback
    if(sent===0){
      var resp2 = UrlFetchApp.fetch(cfg.FIREBASE_URL+"FCMTokens.json?auth="+dbSecret,{muteHttpExceptions:true});
      var tokens = JSON.parse(resp2.getContentText());
      if(tokens && typeof tokens==='object'){
        Object.keys(tokens).forEach(function(phone){
          var token = tokens[phone]&&tokens[phone].token;
          if(token){ var r=sendFCMToToken(token,title,body,extraData||{}); if(r.error)failed++;else sent++; Utilities.sleep(80); }
        });
      }
    }
    return {sent:sent,failed:failed};
  } catch(e) { return {error:e.toString()}; }
}

/* ══ ATOMIC ID ══
   ⚠️ Phase 5: আগে এই ফাংশন পুরনো numeric id (1001, 1002...) জেনারেট করত। এখন থেকে
   নতুন প্রশ্নের id prefix-ভিত্তিক ("QZ-00001" স্টাইল) — RenameTab.jsx-এর
   SHEET_PREFIX (subject_id প্রিফিক্স, "QZ_" স্টাইল)-এর সাথে মিল রেখে বানানো, শুধু
   "_" এর বদলে "-" আর সংখ্যা 5-ডিজিট zero-padded (ExamAppearancesTab.jsx-এর
   placeholder "QB-00123" অনুযায়ী)। পুরনো numeric id-গুলোর (1001, 1002...) সাথে
   কখনো সংঘর্ষ হবে না, কারণ ফরম্যাটই আলাদা (prefix+dash থাকায়) — তাই কোনো
   migration/backfill লাগে না, পুরনো রো-গুলো পুরনো numeric id নিয়েই থাকে, শুধু নতুন
   যা যোগ হবে সেগুলোই নতুন prefix-স্কিমে আসবে। */
var ID_PREFIX = { Quiz: "QZ", QBank: "QB", Study: "ST", Typing: "TY" };

function getNextId(sheetName) {
  var lock = LockService.getScriptLock(); lock.waitLock(15000);
  try {
    var prop = PropertiesService.getScriptProperties();
    var prefix = ID_PREFIX[sheetName] || sheetName.substring(0,2).toUpperCase();
    // পুরনো "MAX_ID_*" property থেকে ইচ্ছাকৃতভাবে আলাদা key — পুরনো numeric
    // কাউন্টারের সাথে এই নতুন prefix-কাউন্টার কখনো mix হবে না
    var key = "NEXT_SEQ_" + sheetName.toUpperCase();
    var seq = parseInt(prop.getProperty(key) || "0");

    // প্রথমবার (property এখনো সেট হয়নি) — Sheet-এ আগে থেকেই কোনো prefix-id
    // (এই সেশনে বা আগে কখনো ম্যানুয়ালি বসানো) থাকলে তার সর্বোচ্চ সংখ্যা থেকে
    // শুরু করা হয়, যাতে ভুলে ডুপ্লিকেট prefix-id তৈরি না হয়। নাহলে 0 থেকে শুরু।
    if (seq === 0) {
      var ss = SpreadsheetApp.getActiveSpreadsheet(), sh = ss.getSheetByName(sheetName);
      if (sh && sh.getLastRow() > 1) {
        var re = new RegExp("^" + prefix + "-(\\d+)$");
        var ids = sh.getRange(2, 1, sh.getLastRow() - 1, 1).getValues();
        for (var i = 0; i < ids.length; i++) {
          var cell = ids[i][0];
          var m = cell !== "" && cell !== null ? cell.toString().match(re) : null;
          if (m) { var n = parseInt(m[1], 10); if (n > seq) seq = n; }
        }
      }
    }

    seq = seq + 1;
    prop.setProperty(key, seq.toString());
    return prefix + "-" + pad5(seq);
  } finally { lock.releaseLock(); }
}

function pad5(n) {
  var s = n.toString();
  while (s.length < 5) s = "0" + s;
  return s;
}

/* ══ DUPLICATE CHECK ══ */
function isDuplicate(sheet, subject, questionText, sub_topic) {
  var data=sheet.getDataRange().getValues(); if(data.length<2)return false;
  var hdr=data[0].map(function(h){return h.toString().toLowerCase().trim();});
  var qIdx=hdr.indexOf("question"),subIdx=hdr.indexOf("subject"),stIdx=hdr.indexOf("sub_topic");
  if(stIdx===-1)stIdx=hdr.indexOf("subtopic");
  // ⚠️ Study ট্যাবের আসল হেডার "sub_topic" না, "topic" (দেখো Study_Database CSV) —
  // ওপরের দুটো মিস হলে এটাও ট্রাই করা হচ্ছে, নইলে Study-তে duplicate-check কখনো
  // sub-topic মেলাতে পারত না (সব সময় ফাঁকা স্ট্রিং ধরে নিত)
  if(stIdx===-1)stIdx=hdr.indexOf("topic");
  if(qIdx===-1)return false;
  var norm=function(s){return s.toString().toLowerCase().replace(/\s+/g,' ').trim().substring(0,100);};
  var nq=norm(questionText),nst=norm(sub_topic||''),nsub=norm(subject||'');
  for(var r=1;r<data.length;r++){
    if(norm(data[r][qIdx])!==nq)continue;
    var rst=stIdx!==-1?norm(data[r][stIdx]):'', rsub=subIdx!==-1?norm(data[r][subIdx]):'';
    if(rst===nst&&rsub===nsub)return true;
  }
  return false;
}

/* ══ FIREBASE SYNC ══ */
// ⛔ HARD NO-FIREBASE LIST — Quiz/QBank/Study এখন সম্পূর্ণভাবে Google Sheet-only।
// User App (student-facing app)-ও এখন Sheet থেকেই ডেটা পড়ে, তাই এই ৩ ট্যাবের জন্য
// আর কোনো Firebase mirror-sync দরকার নেই। syncToFirebase/forceFullRekeySync/syncNFRows
// — এই তিনটা ফাংশনই এই লিস্টের sheet পেলে সাথে সাথে no-op হয়ে {ok:true} রিটার্ন করে,
// কোনো UrlFetchApp কল হয় না। Users/Reports/Notice/Typing-এর মতো ছোট ডেটার জন্য
// Firebase sync আগের মতোই চলবে (এই লিস্টে নেই)।
var NO_FIREBASE_SHEETS = ["Quiz", "QBank", "Study"];

function syncToFirebase(sheetName, folderName) {
  if (NO_FIREBASE_SHEETS.indexOf(sheetName) > -1) return true; // ⛔ Sheet-only, mirror-sync বন্ধ
  try {
    var cfg=getProps(), ss=SpreadsheetApp.getActiveSpreadsheet(), fbSh=ss.getSheetByName(sheetName);
    if(!fbSh)return true;
    var fbData=fbSh.getDataRange().getValues(); if(fbData.length<2)return true;
    var fbHdr=fbData[0];
    if(sheetName==="Reports"){
      var keyedData={};
      for(var i=1;i<fbData.length;i++){
        var rec={}; for(var j=0;j<fbHdr.length;j++){var k=fbHdr[j].toString().trim();if(k){var v=fbData[i][j];rec[k]=(v instanceof Date)?Utilities.formatDate(v,"GMT+6","dd-MM-yyyy HH:mm:ss"):v.toString();}}
        keyedData["row_"+i]=rec;
      }
      var repResp=UrlFetchApp.fetch(cfg.FIREBASE_URL+folderName+".json?auth="+cfg.SECRET_KEY,{method:"put",contentType:"application/json",payload:JSON.stringify(keyedData),muteHttpExceptions:true});
      var repCode=repResp.getResponseCode();
      if(repCode<200||repCode>=300){ Logger.log("Firebase Sync HTTP "+repCode+" ("+sheetName+"): "+repResp.getContentText()); return false; }
      return true;
    }
    var idColIdx=-1, updColIdx=-1;
    for(var h=0;h<fbHdr.length;h++){
      var hl=fbHdr[h].toString().toLowerCase().trim();
      if(hl==="id")idColIdx=h;
      if(hl==="updatedat")updColIdx=h;
    }

    // ⚠️ "id" বা "updatedAt" কলাম নেই এমন sheet (Users/Notice, অথবা "updatedAt" কলাম
    // এখনো যোগ করা হয়নি এমন কোনো sheet) — পুরনো আচরণ (পুরো sheet PUT) অক্ষত রাখা হলো,
    // কারণ এগুলো ছোট sheet, আগের সমস্যার উৎস না, আর "id" ছাড়া PATCH-by-id সম্ভবও না।
    if(idColIdx===-1||updColIdx===-1){
      var jsonDataOld=[];
      for(var io=1;io<fbData.length;io++){
        var reco={}; for(var jo=0;jo<fbHdr.length;jo++){var ko=fbHdr[jo].toString().trim();if(ko){var vo=fbData[io][jo];reco[ko]=(vo instanceof Date)?Utilities.formatDate(vo,"GMT+6","dd-MM-yyyy HH:mm:ss"):vo;}}
        jsonDataOld.push(reco);
      }
      var respOld=UrlFetchApp.fetch(cfg.FIREBASE_URL+folderName+".json?auth="+cfg.SECRET_KEY,{method:"put",contentType:"application/json",payload:JSON.stringify(jsonDataOld),muteHttpExceptions:true});
      var codeOld=respOld.getResponseCode();
      if(codeOld<200||codeOld>=300){ Logger.log("Firebase Sync HTTP "+codeOld+" ("+sheetName+"): "+respOld.getContentText()); return false; }
      return true;
    }

    // ── ✅ Quiz/QBank/Study/Typing — ইনক্রিমেন্টাল sync: শুধু "updatedAt" ভরা row
    //    গুলো (মানে এই ফিক্সের পর নতুন যোগ/এডিট হওয়া) Firebase-এ PATCH হয়, নিজের
    //    "id" কে Firebase key বানিয়ে। "updatedAt" খালি মানে ধরে নেওয়া হয় এই row
    //    আগে থেকেই Firebase-এ আছে (পুরনো এক্সপোর্ট থেকে বসানো) — স্কিপ, পাঠানো হয় না।
    //    পুরো sheet আর কখনো এক ধাক্কায় re-upload হয় না। ──
    var patchData={}, touched=0, touchedRowNums=[];
    var nfColIdx2=-1;
    for(var hh=0;hh<fbHdr.length;hh++){ var nn=fbHdr[hh].toString().toLowerCase().replace(/\s+/g,""); if(nn==="notfirebase"||nn==="nf"){nfColIdx2=hh;break;} }
    for(var i2=1;i2<fbData.length;i2++){
      var updVal=fbData[i2][updColIdx];
      if(!updVal) continue;
      var rowId=fbData[i2][idColIdx];
      if(rowId===""||rowId===null||rowId===undefined) continue;
      var rec2={}; for(var j2=0;j2<fbHdr.length;j2++){var k2=fbHdr[j2].toString().trim();if(k2){var v2=fbData[i2][j2];rec2[k2]=(v2 instanceof Date)?Utilities.formatDate(v2,"GMT+6","dd-MM-yyyy HH:mm:ss"):v2;}}
      patchData[rowId.toString()]=rec2;
      touched++;
      touchedRowNums.push(i2+1); // ১-ইনডেক্সড শিট রো নাম্বার
    }
    if(touched===0) return true; // পাঠানোর কিছুই নেই — নেটওয়ার্ক কলই হবে না

    var resp=UrlFetchApp.fetch(cfg.FIREBASE_URL+folderName+".json?auth="+cfg.SECRET_KEY,{method:"patch",contentType:"application/json",payload:JSON.stringify(patchData),muteHttpExceptions:true});
    var code=resp.getResponseCode();
    if(code<200||code>=300){ Logger.log("Firebase Sync HTTP "+code+" ("+sheetName+"): "+resp.getContentText()); return false; }
    // ✅ সফল হলে এই row গুলোর NF মার্ক মুছে দাও (থাকলে) — শুধু dedicated sync_nf_rows
    // অ্যাকশন না, স্বাভাবিক sync-এও NF ঠিকভাবে ক্লিয়ার হবে।
    if(nfColIdx2!==-1){
      touchedRowNums.forEach(function(r){ fbSh.getRange(r,nfColIdx2+1).setValue(""); });
    }
    // ⏱ meta/updatedAt বাম্প — User App এই ছোট নাম্বারটা আগে চেক করে বোঝে সার্ভারে নতুন
    // কিছু আছে কিনা, তারপরই দরকার হলে পুরো/delta ফেচ করে। শুধু Quiz/QBank/Study-এর
    // জন্যই বাম্প হয় (এগুলোই delta-sync হয়, Users/Notice/Reports/Typing না)।
    if(["Quiz","QBank","Study"].indexOf(sheetName)>-1){
      try{
        UrlFetchApp.fetch(cfg.FIREBASE_URL+"meta/updatedAt.json?auth="+cfg.SECRET_KEY,
          {method:"put",contentType:"application/json",payload:JSON.stringify(Date.now()),muteHttpExceptions:true});
      }catch(me){ Logger.log("meta/updatedAt bump error: "+me.toString()); }
    }
    return true;
  } catch(e){ Logger.log("Firebase Sync Error ("+sheetName+"): "+e.toString()); return false; }
}

/* ── ⚠️ এক-বারের, ইচ্ছাকৃত, ম্যানুয়াল অ্যাকশন — পুরনো Firebase ডেটা (যেটা array-index
   দিয়ে key করা ছিল, "id" দিয়ে না) নতুন করে "id" দিয়ে re-key করে পুরো sheet একবার PUT
   করে। এটা করা ছাড়া উপরের ইনক্রিমেন্টাল PATCH ভবিষ্যতে পুরনো (already-in-Firebase)
   কোনো row এডিট হলে ডুপ্লিকেট বানিয়ে ফেলবে (পুরনো array-key এন্ট্রি + নতুন id-key
   এন্ট্রি দুটোই থেকে যাবে)। তাই deploy করার পর, প্রথম ইনক্রিমেন্টাল sync-এর ওপর ভরসা
   করার আগে, প্রতিটা sheet-এ এই অ্যাকশনটা একবার চালিয়ে নেওয়া জরুরি। এটা "write"
   (upload), যেটা "Downloads" কোটার (যেটা exceeded হয়েছিল) সাথে সম্পর্কিত না, আর
   User App সরাসরি REST দিয়ে পড়ে (live listener না), তাই এই এক-বারের write কোনো
   ডিভাইসেই বাড়তি download ট্রিগার করে না। ── */
function forceFullRekeySync(sheetName, folderName){
  if (NO_FIREBASE_SHEETS.indexOf(sheetName) > -1) return {ok:true, msg:"⛔ "+sheetName+" এখন Sheet-only — Firebase rekey স্কিপ করা হলো"};
  try{
    var cfg=getProps(), ss=SpreadsheetApp.getActiveSpreadsheet(), fbSh=ss.getSheetByName(sheetName);
    if(!fbSh) return {ok:false,msg:"Sheet not found: "+sheetName};
    var fbData=fbSh.getDataRange().getValues(); if(fbData.length<2) return {ok:true,msg:"খালি sheet, কিছু করার নেই"};
    var fbHdr=fbData[0].map(function(h){return h.toString().trim();});
    var idColIdx=-1;
    for(var h=0;h<fbHdr.length;h++){ if(fbHdr[h].toLowerCase()==="id"){idColIdx=h;break;} }
    if(idColIdx===-1) return {ok:false,msg:"এই sheet-এ 'id' কলাম নেই, id-keyed rekey সম্ভব না"};
    var keyed={};
    for(var i=1;i<fbData.length;i++){
      var rowId=fbData[i][idColIdx]; if(rowId===""||rowId===null||rowId===undefined) continue;
      var rec={}; for(var j=0;j<fbHdr.length;j++){var k=fbHdr[j];if(k){var v=fbData[i][j];rec[k]=(v instanceof Date)?Utilities.formatDate(v,"GMT+6","dd-MM-yyyy HH:mm:ss"):v;}}
      keyed[rowId.toString()]=rec;
    }
    var resp=UrlFetchApp.fetch(cfg.FIREBASE_URL+folderName+".json?auth="+cfg.SECRET_KEY,{method:"put",contentType:"application/json",payload:JSON.stringify(keyed),muteHttpExceptions:true});
    var code=resp.getResponseCode();
    if(code<200||code>=300) return {ok:false,msg:"HTTP "+code+": "+resp.getContentText()};
    if(["Quiz","QBank","Study"].indexOf(sheetName)>-1){
      try{
        UrlFetchApp.fetch(cfg.FIREBASE_URL+"meta/updatedAt.json?auth="+cfg.SECRET_KEY,
          {method:"put",contentType:"application/json",payload:JSON.stringify(Date.now()),muteHttpExceptions:true});
      }catch(me){}
    }
    return {ok:true,msg:"re-key সম্পূর্ণ, "+Object.keys(keyed).length+" টা row Firebase-এ id দিয়ে key করা হলো"};
  }catch(e){ return {ok:false,msg:e.toString()}; }
}

/* ── ✅ NF (Not Firebase)-marked row sync — ম্যানুয়ালি "Not Firebase"/"NF" কলামে
   মার্ক করা row গুলোই শুধু Firebase-এ PATCH করে (id দিয়ে key করে, নতুন রো বলে
   কোনো পুরনো key-এর সাথে সংঘর্ষের ঝুঁকি নেই)। সফল হলে সেই row-এর NF মার্ক মুছে
   দেয় ও updatedAt বসিয়ে দেয় (sheet-এও), যাতে দ্বিতীয়বার ভুলে আবার sync না হয়।
   এটা force_full_rekey_sync-এর ছোট, নিরাপদ, targeted বিকল্প — যখন শুধু নির্দিষ্ট
   কিছু row-ই নতুন (পুরো sheet না), তখন এটাই ব্যবহার করা ভালো। ── */
function syncNFRows(sheetName, folderName){
  if (NO_FIREBASE_SHEETS.indexOf(sheetName) > -1) return {ok:true, msg:"⛔ "+sheetName+" এখন Sheet-only — Firebase NF-sync স্কিপ করা হলো", count:0};
  try{
    var cfg=getProps(), ss=SpreadsheetApp.getActiveSpreadsheet(), sh=ss.getSheetByName(sheetName);
    if(!sh) return {ok:false,msg:"Sheet not found: "+sheetName,count:0};
    var data=sh.getDataRange().getValues(); if(data.length<2) return {ok:true,msg:"খালি sheet",count:0};
    var hdr=data[0].map(function(h){return h.toString().trim();});
    var idColIdx=-1, nfColIdx=-1, updColIdx=-1;
    for(var h=0;h<hdr.length;h++){
      var norm=hdr[h].toLowerCase().replace(/\s+/g,"");
      if(norm==="id") idColIdx=h;
      if(norm==="notfirebase"||norm==="nf") nfColIdx=h;
      if(norm==="updatedat") updColIdx=h;
    }
    if(idColIdx===-1) return {ok:false,msg:"'id' কলাম নেই",count:0};
    if(nfColIdx===-1) return {ok:true,msg:"এই sheet-এ 'Not Firebase' কলাম নেই — কিছু করার নেই",count:0};

    var patchData={}, touchedRows=[], nowMs=Date.now();
    for(var i=1;i<data.length;i++){
      var nfVal=(data[i][nfColIdx]||"").toString().trim();
      if(!nfVal) continue; // NF মার্ক নেই — আগে থেকেই Firebase-এ আছে ধরে নেওয়া হচ্ছে, স্কিপ
      var rowId=data[i][idColIdx]; if(rowId===""||rowId===null||rowId===undefined) continue;
      var rec={};
      for(var j=0;j<hdr.length;j++){
        if(j===nfColIdx) continue; // NF মার্কার কলাম নিজে Firebase-এ যাবে না
        var k=hdr[j]; if(!k) continue;
        var v=data[i][j];
        rec[k]=(v instanceof Date)?Utilities.formatDate(v,"GMT+6","dd-MM-yyyy HH:mm:ss"):v;
      }
      if(updColIdx!==-1) rec[hdr[updColIdx]]=nowMs;
      patchData[rowId.toString()]=rec;
      touchedRows.push(i+1); // ১-ইনডেক্সড শিট রো নাম্বার
    }

    var count=Object.keys(patchData).length;
    if(count===0) return {ok:true,msg:"কোনো NF-marked row নেই, পাঠানোর কিছু নেই",count:0};

    var resp=UrlFetchApp.fetch(cfg.FIREBASE_URL+folderName+".json?auth="+cfg.SECRET_KEY,{method:"patch",contentType:"application/json",payload:JSON.stringify(patchData),muteHttpExceptions:true});
    var code=resp.getResponseCode();
    if(code<200||code>=300) return {ok:false,msg:"HTTP "+code+": "+resp.getContentText(),count:0};

    // ✅ সফল হলে NF মার্ক মুছে + updatedAt সেলেও বসিয়ে দাও, যাতে দ্বিতীয়বার আবার sync না হয়
    touchedRows.forEach(function(r){
      sh.getRange(r, nfColIdx+1).setValue("");
      if(updColIdx!==-1) sh.getRange(r, updColIdx+1).setValue(nowMs);
    });

    if(["Quiz","QBank","Study"].indexOf(sheetName)>-1){
      try{
        UrlFetchApp.fetch(cfg.FIREBASE_URL+"meta/updatedAt.json?auth="+cfg.SECRET_KEY,
          {method:"put",contentType:"application/json",payload:JSON.stringify(nowMs),muteHttpExceptions:true});
      }catch(me){}
    }
    return {ok:true,msg:count+" টা NF-marked row Firebase-এ পাঠানো হলো, NF মার্ক মুছে দেওয়া হলো",count:count};
  }catch(e){ return {ok:false,msg:e.toString(),count:0}; }
}

// ══════════════════════════════════════════════════════════════════════
// runRebuildArchiveIndexCore — Archive-এর জন্য runRebuildIndexCore()-এর
// প্যারালাল ভার্সন (নতুন, সম্পূর্ণ additive)। এটা না চালালে "Topics Archive"
// শিটের row_start_quiz/row_count_quiz/row_start_qbank/row_count_qbank
// কলামগুলো খালিই থাকে, ফলে getArchiveQuestionsPage-এর fast-path কখনো ট্রিগার
// হয় না আর app-এর টপিক-লিস্টে (যেটা rowCountFor() দিয়ে filter করে) প্রায় সব
// টপিক 0 দেখায়/বাদ পড়ে যায় — এটাই ঠিক যে বাগ রিপোর্ট হয়েছিল তার আসল কারণ।
//
// ⚠️ ONE-TIME (+মাঝেমধ্যে) — ম্যানুয়ালি একবার চালাতে হবে (browser দিয়ে
// action=rebuildArchiveIndex কল করে, বা Apps Script এডিটর থেকে সরাসরি এই
// ফাংশন Run করে), তারপর শুধু তখনই আবার চালানো লাগবে যখন আপনি নিজে Sheet-এ
// গিয়ে ম্যানুয়ালি duplicate-ট্যাগ করা রো ডিলিট করবেন (কারণ তখন row position
// শিফট হয়ে যাবে, index স্টেল হয়ে যাবে)। archiveMarkDuplicate/
// archiveMoveToActive — এই দুটো action কখনো row shift করে না, তাই এগুলোর
// পরে আবার রান করার দরকার নেই।
// ──────────────────────────────────────────────────────────────────────
function runRebuildArchiveIndexCore() {
  var raibResults={};
  // ⚠️ এই ম্যাপ ইচ্ছাকৃতভাবে doGet-এর ভিতরের ARCHIVE_SHEET_MAP_-এর হুবহু কপি —
  // এই ফাংশনটা top-level (doGet-এর বাইরে, যাতে Apps Script এডিটর থেকে সরাসরি
  // Run করা যায়), তাই doGet-এর ভিতরের var আলাদা স্কোপে থাকায় এখানে পুনরায়
  // লেখা হলো। দুটো জায়গায় sheet/column নাম বদলালে দুটোই আপডেট করতে হবে।
  var raibSheets=[
    {name:"Quiz-Archive",  rsCol:"row_start_quiz",  rcCol:"row_count_quiz"},
    {name:"QBank-Archive", rsCol:"row_start_qbank", rcCol:"row_count_qbank"}
  ];
  var raibSs=SpreadsheetApp.getActiveSpreadsheet();
  var raibTopicsSh=raibSs.getSheetByName("Topics Archive");
  if (!raibTopicsSh) return {error:"'Topics Archive' শিট পাওয়া যায়নি"};
  var raibTopicsData=raibTopicsSh.getDataRange().getValues();
  var raibTopicsHdr=raibTopicsData[0]||[];
  var raibNumTopicRows=Math.max(raibTopicsData.length-1,0);
  var raibTIdCol=raibTopicsHdr.indexOf("topic_id");
  if (raibTIdCol<0) return {error:"'Topics Archive'-এ 'topic_id' কলাম নেই"};

  var raibColBuffers={}; // colIndex -> array[raibNumTopicRows]
  function raibGetBuffer(colIdx){
    if (!raibColBuffers[colIdx]) {
      var buf=new Array(raibNumTopicRows);
      for (var bi=0;bi<raibNumTopicRows;bi++) buf[bi]="";
      raibColBuffers[colIdx]=buf;
    }
    return raibColBuffers[colIdx];
  }

  for (var rs=0; rs<raibSheets.length; rs++){
    var raibShName=raibSheets[rs].name;
    var raibSh=raibSs.getSheetByName(raibShName);
    if (!raibSh || raibSh.getLastRow()<2) { raibResults[raibShName]="sheet ফাঁকা/নেই — skip"; continue; }
    var raibData=raibSh.getDataRange().getValues();
    var raibHdr=raibData[0];
    var raibTopCol=raibHdr.indexOf("topic_id");
    if (raibTopCol<0) { raibResults[raibShName]="'topic_id' কলাম নেই — skip"; continue; }

    // ── existing runRebuildIndexCore()-এর মতোই — sort করে contiguous ব্লক বানানো ──
    var raibSubCol=raibHdr.indexOf("subject_id");
    var raibSortCols=[];
    if (raibSubCol>=0) raibSortCols.push({column:raibSubCol+1,ascending:true});
    raibSortCols.push({column:raibTopCol+1,ascending:true});
    raibSh.getRange(2,1,raibSh.getLastRow()-1,raibSh.getLastColumn()).sort(raibSortCols);

    var raibData2=raibSh.getDataRange().getValues();
    var raibIndexMap={};
    var curTopic=null, curStart=2, curCount=0;
    for (var i5=1;i5<raibData2.length;i5++){
      var tId=(raibData2[i5][raibTopCol]||"").toString();
      if (tId!==curTopic) {
        if (curTopic) raibIndexMap[curTopic]={start:curStart,count:curCount};
        curTopic=tId; curStart=i5+1; curCount=0;
      }
      curCount++;
    }
    if (curTopic) raibIndexMap[curTopic]={start:curStart,count:curCount};
    raibResults[raibShName]="sorted, "+(raibData2.length-1)+" rows, "+Object.keys(raibIndexMap).length+" topics";

    var raibPair=raibSheets[rs];
    var raibRsC=raibTopicsHdr.indexOf(raibPair.rsCol), raibRcC=raibTopicsHdr.indexOf(raibPair.rcCol);
    if (raibRsC<0 || raibRcC<0) { raibResults[raibShName]+=" | ⚠️ Topics Archive-এ "+raibPair.rsCol+"/"+raibPair.rcCol+" কলাম নেই"; continue; }
    var raibRsBuf=raibGetBuffer(raibRsC), raibRcBuf=raibGetBuffer(raibRcC);
    for (var t2=1;t2<raibTopicsData.length;t2++){
      var raibTid=(raibTopicsData[t2][raibTIdCol]||"").toString();
      var raibEntry=raibIndexMap[raibTid];
      var bufIdx=t2-1;
      if (raibEntry) { raibRsBuf[bufIdx]=raibEntry.start; raibRcBuf[bufIdx]=raibEntry.count; }
      else { raibRsBuf[bufIdx]=""; raibRcBuf[bufIdx]=""; }
    }
  }

  if (raibTopicsSh && raibNumTopicRows>0) {
    Object.keys(raibColBuffers).forEach(function(colIdxStr){
      var colIdx=parseInt(colIdxStr,10);
      var buf=raibColBuffers[colIdx];
      raibTopicsSh.getRange(2,colIdx+1,raibNumTopicRows,1).setValues(buf.map(function(v){return [v];}));
    });
  }
  return raibResults;
}

/* ══════════════════════════════════════════════════════════
   doGet
══════════════════════════════════════════════════════════ */
// ── runRebuildIndexCore — rebuildIndex action-এর আসল লজিক, রিইউজযোগ্য ফাংশনে
// বের করে আনা হলো (আগে এটা শুধু action==="rebuildIndex" HTTP handler-এর ভিতরেই
// ছিল, ম্যানুয়ালি কল করতে হতো)। এখন এই একই ফাংশন moveQuestions/moveTopic/
// deleteByIds/deleteByReferenceId-এর শেষে automatic-ভাবেও কল হয় (নিচে দেখো),
// আর installAutoReindexTrigger()-এর periodic safety-net trigger থেকেও। ──
function runRebuildIndexCore() {
  var ribResults={};
  var ribSheets=[{name:"Quiz",prefix:"subject_id"},{name:"QBank",prefix:"subject_id"},{name:"Study",prefix:"subject_id"}];
  var ribSs=SpreadsheetApp.getActiveSpreadsheet();
  var ribTopicsSh=ribSs.getSheetByName("Topics");
  var ribTopicsData=ribTopicsSh?ribTopicsSh.getDataRange().getValues():[];
  var ribTopicsHdr=ribTopicsData[0]||[];
  var ribNumTopicRows=Math.max(ribTopicsData.length-1,0); // header বাদে ডেটা-রো সংখ্যা

  // ── FIX (bug: Quiz/Study-তে প্রশ্ন 0 দেখাতো যদিও QBank-এ ঠিক দেখাতো) ──
  // আগে row_start/row_count Topics-এ মাত্র ১টা কলাম-জোড়া ছিল, আর নিচের লুপে
  // একটাই shared ribIndexMap (শুধু topic_id দিয়ে key করা) Quiz→QBank→Study
  // তিনটা শিট প্রসেস করতো। কোনো topic_id একাধিক শিটে (যেমন Quiz আর QBank দুটোতেই)
  // থাকলে পরের শিট আগেরটার index চুপচাপ ওভাররাইট করে দিতো — ফলে Quiz browse
  // করার সময় getQuestionsPage ভুল sheet-এর row-range Quiz ট্যাবে apply করতে
  // যেতো (range Quiz ট্যাবের বাইরে পড়লে getRange() এরর দেয়, ক্লায়েন্টে সেটাই
  // "কোনো প্রশ্ন পাওয়া যায়নি — ইন্টারনেট চেক করো" হয়ে দেখা যায়)।
  // এখন প্রতিটা শিটের জন্য আলাদা row_start_<sheet>/row_count_<sheet> কলাম-জোড়া
  // রাখা হচ্ছে, তাই কোনো ওভাররাইট হয় না — একই topic_id তিন শিটেই থাকলেও
  // প্রতিটার নিজের সঠিক row-range নিজের কলামে থাকে। ──
  var ribColPairs={}; // sheetName -> {rsCol, rcCol}
  for (var rp=0;rp<ribSheets.length;rp++){
    var ribPName=ribSheets[rp].name;
    var ribRsColName="row_start_"+ribPName.toLowerCase();
    var ribRcColName="row_count_"+ribPName.toLowerCase();
    var ribRsC=ribTopicsHdr.indexOf(ribRsColName), ribRcC=ribTopicsHdr.indexOf(ribRcColName);
    if (ribTopicsSh && ribRsC<0) { ribTopicsSh.getRange(1,ribTopicsHdr.length+1).setValue(ribRsColName); ribRsC=ribTopicsHdr.length; ribTopicsHdr.push(ribRsColName); }
    if (ribTopicsSh && ribRcC<0) { ribTopicsSh.getRange(1,ribTopicsHdr.length+1).setValue(ribRcColName); ribRcC=ribTopicsHdr.length; ribTopicsHdr.push(ribRcColName); }
    ribColPairs[ribPName]={rsCol:ribRsC,rcCol:ribRcC};
  }
  // ⚠️ legacy generic row_start/row_count কলাম থাকলেও রেখে দেওয়া হলো (পুরনো ক্লায়েন্ট/
  // স্ক্রিপ্ট এখনো পড়তে পারে বলে), কিন্তু নতুন লজিক এখন এগুলোর ওপর নির্ভর করে না।
  var ribLegacyRsCol=ribTopicsHdr.indexOf("row_start"), ribLegacyRcCol=ribTopicsHdr.indexOf("row_count");

  // ── QUOTA/স্পিড ফিক্স ("অটোমেশনের জন্য লিমিট খাব না তো?"): আগে প্রতিটা Topic-রো,
  // প্রতিটা কলামের জন্য আলাদা getRange().setValue() কল হতো — মানে টপিক-সংখ্যা × sheet ×
  // কলাম-সংখ্যা যতগুলো, ততগুলো আলাদা Sheets API কল (কয়েকশো/হাজার টপিক থাকলে এটাই সবচেয়ে
  // ধীর অংশ ছিল, GAS-এর ৬-মিনিট/এক্সিকিউশন লিমিটে ধাক্কা খাওয়ার ঝুঁকি তৈরি করতো)। এখন সব
  // মান আগে মেমোরিতে (in-memory array) জমিয়ে শেষে কলাম-প্রতি মাত্র ১টা batch setValues()
  // কল করা হয় — টপিক-সংখ্যা যতই হোক না কেন, মোট কল-সংখ্যা এখন ধ্রুবক (কয়েক-ডজন, sheet ও
  // কলাম-সংখ্যার ওপর নির্ভর করে, টপিক-সংখ্যার ওপর না)। ──
  var ribColBuffers={}; // colIndex -> array[ribNumTopicRows] of value (pre-filled "")
  function ribGetBuffer(colIdx){
    if (!ribColBuffers[colIdx]) {
      var buf=new Array(ribNumTopicRows);
      for (var bi=0;bi<ribNumTopicRows;bi++) buf[bi]="";
      ribColBuffers[colIdx]=buf;
    }
    return ribColBuffers[colIdx];
  }

  for (var rs=0;rs<ribSheets.length;rs++) {
    var ribShName=ribSheets[rs].name;
    var ribSh=ribSs.getSheetByName(ribShName);
    if (!ribSh || ribSh.getLastRow()<2) continue;
    var ribRange=ribSh.getDataRange();
    var ribData=ribRange.getValues();
    var ribHdr=ribData[0];
    var ribSubCol=ribHdr.indexOf("subject_id"), ribTopCol=ribHdr.indexOf("topic_id");
    if (ribSubCol<0) { ribResults[ribShName]="subject_id column missing — skip"; continue; }
    // sort by subject_id, topic_id (header বাদে)
    var ribSortCols=[{column:ribSubCol+1,ascending:true}];
    if (ribTopCol>=0) ribSortCols.push({column:ribTopCol+1,ascending:true});
    ribSh.getRange(2,1,ribSh.getLastRow()-1,ribSh.getLastColumn()).sort(ribSortCols);
    // re-read after sort, build contiguous ranges per topic_id — এই শিটের নিজস্ব ম্যাপে
    var ribIndexMap={}; // topic_id -> {start,count} — শুধু এই sheet-এর জন্য, আলাদা প্রতিবার
    var ribData2=ribSh.getDataRange().getValues();
    var curTopic=null, curStart=2, curCount=0;
    for (var i5=1;i5<ribData2.length;i5++){
      var tId=ribTopCol>=0?(ribData2[i5][ribTopCol]||"").toString():"";
      if (tId!==curTopic) {
        if (curTopic) ribIndexMap[curTopic]={start:curStart,count:curCount};
        curTopic=tId; curStart=i5+1; curCount=0;
      }
      curCount++;
    }
    if (curTopic) ribIndexMap[curTopic]={start:curStart,count:curCount};
    ribResults[ribShName]="sorted, "+(ribData2.length-1)+" rows";

    // এই শিটের row_start_<sheet>/row_count_<sheet> মান memory-buffer-এ বসাও (এখনো
    // কোনো Sheets API কল না — সব শেষে একসাথে ফ্লাশ হবে)
    if (ribTopicsSh) {
      var ribPair=ribColPairs[ribShName];
      var ribTIdCol=ribTopicsHdr.indexOf("topic_id");
      var ribRsBuf=ribGetBuffer(ribPair.rsCol), ribRcBuf=ribGetBuffer(ribPair.rcCol);
      var ribLegacyRsBuf=ribLegacyRsCol>=0?ribGetBuffer(ribLegacyRsCol):null;
      var ribLegacyRcBuf=ribLegacyRcCol>=0?ribGetBuffer(ribLegacyRcCol):null;
      for (var t2=1;t2<ribTopicsData.length;t2++){
        var ribTid=(ribTopicsData[t2][ribTIdCol]||"").toString();
        var ribEntry=ribIndexMap[ribTid];
        var bufIdx=t2-1;
        if (ribEntry) {
          ribRsBuf[bufIdx]=ribEntry.start;
          ribRcBuf[bufIdx]=ribEntry.count;
          // legacy কলাম থাকলে সর্বশেষ প্রসেস হওয়া শিট দিয়ে রেফারেন্সের জন্য আপডেট (backward-compat only)
          if (ribLegacyRsBuf) ribLegacyRsBuf[bufIdx]=ribEntry.start;
          if (ribLegacyRcBuf) ribLegacyRcBuf[bufIdx]=ribEntry.count;
        } else {
          // এই sheet-এ এই topic_id-এর কোনো রো নেই — "" রাখা হলো (ribGetBuffer-এর ডিফল্ট),
          // নইলে পুরনো row_start_quiz স্টেল/ভুল range নিয়ে fast-path ভুলভাবে ট্রিগার হতে পারে
          ribRsBuf[bufIdx]=""; ribRcBuf[bufIdx]="";
        }
      }
    }
  }

  // ── একদম শেষে — কলাম-প্রতি মাত্র ১টা batch write (ধ্রুবক সংখ্যক API কল) ──
  if (ribTopicsSh && ribNumTopicRows>0) {
    Object.keys(ribColBuffers).forEach(function(colIdxStr){
      var colIdx=parseInt(colIdxStr,10);
      var buf=ribColBuffers[colIdx];
      ribTopicsSh.getRange(2,colIdx+1,ribNumTopicRows,1).setValues(buf.map(function(v){return [v];}));
    });
  }
  return ribResults;
}

// ── AUTO-REINDEX — QUOTA-নিরাপদ ডিজাইন ("অটোমেশনের জন্য লিমিট খাব না তো?") ──
// moveQuestions/moveTopic/deleteByIds/deleteByReferenceId — প্রথমে এই ৪টা action
// প্রতিটার শেষেই সরাসরি runRebuildIndexCore() (ভারী, পুরো ৩-শিট সর্ট) সিঙ্ক্রোনাসলি
// কল করতো। সমস্যা: admin একই সেশনে বারবার move করলে প্রতিবারই এই ভারী কাজ পুরো
// শেষ না হওয়া পর্যন্ত move/delete-এর রেসপন্সই আটকে থাকতো (ধীর UX), আর বড়
// ডেটাসেটে GAS-এর প্রতি-এক্সিকিউশন ৬-মিনিট লিমিটে ধাক্কা খাওয়ার ঝুঁকি ছিল।
//
// এখন সেই ৪টা action শুধু markReindexNeeded_() কল করে — এটা PropertiesService-এ
// একটা "dirty" ফ্ল্যাগ বসায়, নিজে কোনো ভারী কাজ করে না (এক-মিলিসেকেন্ডের কম) —
// তাই move/delete-এর রেসপন্স সাথে সাথেই ফেরত যায়, কোনো অপেক্ষা নেই।
//
// আসল ভারী কাজ (runRebuildIndexCore, batch-write করা, দেখো ওপরের কমেন্ট) শুধু
// পর্যায়ক্রমিক ট্রিগারে (প্রতি ১৫ মিনিটে একবার) চলে, আর তাও শুধু ফ্ল্যাগ সেট থাকলেই —
// একই ১৫-মিনিট উইন্ডোতে ১০টা move হলেও রিইনডেক্স চলে মাত্র ১বার (debounce)। ফলে দিনে
// সর্বোচ্চ ৯৬টা ট্রিগার-এক্সিকিউশন (২৪×৪), তার বেশিরভাগই ফ্ল্যাগ না থাকলে সাথে সাথে
// বেরিয়ে যায় (প্রায় ফ্রি) — Apps Script-এর দৈনিক রানটাইম কোটার (consumer অ্যাকাউন্টে
// সাধারণত ~৯০ মিনিট/দিন) কাছাকাছিও যাবে না।
//
// ⚠️ ONE-TIME SETUP (এটা কোডে বসিয়ে দিলেই অটো চলে না — একবার ম্যানুয়ালি রান
// করতে হবে): Apps Script এডিটরে এই ফাইল খুলে, ফাংশন ড্রপডাউন থেকে
// "installAutoReindexTrigger" বেছে নিয়ে ▶ Run বাটনে একবার ক্লিক করো (প্রথমবার
// authorization চাইতে পারে, allow করে দিও)। এরপর থেকে সারাজীবন প্রতি ১৫ মিনিটে
// নিজে থেকেই চেক করবে, কোনো ম্যানুয়াল rebuildIndex আর লাগবে না। দ্বিতীয়বার রান
// করলে আগের ট্রিগার মুছে নতুন বসায় — ডুপ্লিকেট জমবে না। ──
var REINDEX_FLAG_KEY_ = "NEEDS_REINDEX";

// ── SELF-INSTALLING TRIGGER (স্থায়ী সমাধানের ২য় অংশ): installAutoReindexTrigger()
// আগে Apps Script এডিটরে গিয়ে একবার ম্যানুয়ালি ▶ Run করা লাগতো — বাস্তবে এই
// এক-বারের ধাপটাই প্রায় কখনো করা হতো না, ফলে ফ্ল্যাগ সেট হলেও কেউ কখনো চেক
// করতো না, নতুন Topic কখনো index-এ ঢুকতোই না। এখন প্রতিটা markReindexNeeded_()
// কলেই (একটা সস্তা Script Property চেক দিয়ে) যাচাই হয় ১৫-মিনিটের ট্রিগারটা
// বসানো আছে কিনা — না থাকলে নিজে থেকেই বসিয়ে দেয়। ফ্ল্যাগ একবার সেট হয়ে গেলে
// পরের কলগুলোতে আর ScriptApp.getProjectTriggers() পর্যন্ত যেতেই হয় না (সস্তা)।
var REINDEX_TRIGGER_INSTALLED_KEY_ = "REINDEX_TRIGGER_INSTALLED_V1";

function markReindexNeeded_() {
  try {
    var props = PropertiesService.getScriptProperties();
    props.setProperty(REINDEX_FLAG_KEY_, "1");
    if (props.getProperty(REINDEX_TRIGGER_INSTALLED_KEY_) !== "1") {
      try {
        installAutoReindexTrigger();
        props.setProperty(REINDEX_TRIGGER_INSTALLED_KEY_, "1");
      } catch (instErr) {
        logError_("markReindexNeeded_/autoInstallTrigger", String(instErr));
      }
    }
  }
  catch (flagErr) { logError_("markReindexNeeded_", flagErr); }
}

function installAutoReindexTrigger() {
  var triggers=ScriptApp.getProjectTriggers();
  for (var i=0;i<triggers.length;i++){
    if (triggers[i].getHandlerFunction()==="autoRebuildIndexTriggered") ScriptApp.deleteTrigger(triggers[i]);
  }
  ScriptApp.newTrigger("autoRebuildIndexTriggered").timeBased().everyMinutes(15).create();
  Logger.log("✅ Auto-reindex trigger installed — প্রতি ১৫ মিনিটে চেক করবে, দরকার হলেই (dirty ফ্ল্যাগ থাকলে) রিইনডেক্স চলবে।");
}

function autoRebuildIndexTriggered() {
  try {
    var props=PropertiesService.getScriptProperties();
    if (props.getProperty(REINDEX_FLAG_KEY_)!=="1") return; // কিছু বদলায়নি — সস্তায় সাথে সাথে বেরিয়ে যাও
    // ফ্ল্যাগ আগেই ক্লিয়ার করা হচ্ছে (রিইনডেক্স চলাকালীন নতুন move এলে সেটা মিস না হয় —
    // মিস হলেও ক্ষতি নেই, পরের ১৫-মিনিট সাইকেলেই ধরা পড়বে যেহেতু move নিজেই আবার ফ্ল্যাগ সেট করবে)
    props.deleteProperty(REINDEX_FLAG_KEY_);
    var result=runRebuildIndexCore();
    Logger.log("autoRebuildIndexTriggered: "+JSON.stringify(result));
  } catch (err) {
    logError_("autoRebuildIndexTriggered", "reindex failed: "+err);
  }
}

function doGet(e) {
 try {
  var action = e.parameter.action;

  // ── version — secret ছাড়াই, ব্রাউজারে সরাসরি GAS_URL+"?action=version" খুলে
  // ডিপ্লয়মেন্ট আপডেট হয়েছে কিনা যাচাই। দেখো ওপরের GAS_BUILD_VERSION কমেন্ট। ──
  if (action==="version") {
    return json({ build: GAS_BUILD_VERSION, now: new Date().toString() });
  }

  // ── examDiag — secret ছাড়াই, ব্রাউজারে সরাসরি GAS_URL+"?action=examDiag"
  // খুলে সবশেষ exam-appearances.json publish attempt-এর ফলাফল (rows/bytes/
  // success-error) দেখা যায়। দেখো doPublish_()-এর ভেতরের DIAG কমেন্ট। ──
  if (action==="examDiag") {
    var eaDiagRaw = PropertiesService.getScriptProperties().getProperty("LAST_EXAM_APPEARANCES_DIAG");
    return json(eaDiagRaw ? JSON.parse(eaDiagRaw) : { message: "এখনো কোনো publish attempt রেকর্ড হয়নি" });
  }

  // ── refDiag — secret ছাড়াই, ব্রাউজারে সরাসরি GAS_URL+"?action=refDiag" খুলে
  // সবশেষ Publish-এ subjects/topics/tags/posts/institutions/exam-appearances —
  // প্রতিটা reference ফাইল আলাদাভাবে সফল/ব্যর্থ হয়েছিল কিনা দেখা যায়। দেখো
  // doPublish_()-এর ভেতরের DIAG কমেন্ট (refPublishOne_)। ──
  if (action==="refDiag") {
    var refDiagRaw = PropertiesService.getScriptProperties().getProperty("LAST_REFERENCE_DIAG");
    return json(refDiagRaw ? JSON.parse(refDiagRaw) : { message: "এখনো কোনো publish attempt রেকর্ড হয়নি" });
  }

  var cfg    = getProps();

  // ── SECRET_KEY VALIDATION ──
  var expectedSecret = cfg.SECRET_KEY;
  var receivedSecret = e.parameter.secret || "";
  if (expectedSecret && receivedSecret !== expectedSecret) {
    return json({ status: "error", message: "Unauthorized" });
  }

  // ── verifyLogin ──
  if (action==="verifyLogin") {
    var phone=(e.parameter.phone||"").toString().trim().replace(/\s/g,'');
    var pass=(e.parameter.password||"").toString().trim();
    if(!phone||!pass)return json({result:"error",error:"missing credentials"});
    var ss=SpreadsheetApp.getActiveSpreadsheet(), uSh=ss.getSheetByName("Users");
    if(!uSh)return json({result:"error",error:"Users sheet not found"});
    var uData=uSh.getDataRange().getValues();
    var uHdr=uData[0].map(function(h){return h.toString().toLowerCase().trim();});
    var phCol=uHdr.indexOf("phone"), pwCol=uHdr.indexOf("password");
    var normP=phone.replace(/^0+/,''), hashedPass=hashPassword(pass);
    for(var i=1;i<uData.length;i++){
      var uPhone=(phCol!==-1?uData[i][phCol]:"").toString().trim().replace(/['\s]/g,'');
      var normU=uPhone.replace(/^0+/,'');
      if(uPhone!==phone&&normU!==normP)continue;
      var storedPass=(pwCol!==-1?uData[i][pwCol]:"").toString().trim();
      if(storedPass!==hashedPass&&storedPass!==pass)return json({result:"error",error:"wrong password"});
      var rec={}; for(var j=0;j<uHdr.length;j++){var key=uData[0][j].toString().trim();if(key)rec[key]=uData[i][j].toString();}
      return json({result:"success",user:rec});
    }
    return json({result:"error",error:"user not found"});
  }

  // ── updateField ──
  if (action==="updateField") {
    return withWriteLock(function(){
    var ss=SpreadsheetApp.getActiveSpreadsheet();
    var shName=e.parameter.sheet||"";
    var shMap={quiz:"Quiz",qbank:"QBank",study:"Study",users:"Users",typing:"Typing"};
    shName=shMap[shName.toLowerCase()]||shName;
    var uSheet=ss.getSheetByName(shName);
    if(!uSheet)return json({result:"error",error:"Sheet not found: "+shName});
    var uRows=uSheet.getDataRange().getValues();
    var uHdr=uRows[0].map(function(h){return h.toString().toLowerCase().trim();});
    // ── normalize: শুধু a-z0-9 রেখে বাকি সব ফেলে দেওয়া — bulk_save_rows-এর
    // buildRowArray ঠিক এই normalize দিয়েই কলাম বসায়, তাই actual header
    // "Sub Topic" (স্পেস) হোক বা "sub_topic" (আন্ডারস্কোর) হোক বা "SubTopic"
    // — সব normalize করলে "subtopic" হয়ে যায় এবং মিলে যায়। আগে শুধু
    // lowercase+trim দিয়ে exact/substring চেক হতো, যেটা স্পেস/আন্ডারস্কোর
    // ভিন্নতায় miss করত — Review Tab-এ "সেভ ব্যর্থ, ফিল্ড: sub_topic" বাগের
    // মূল কারণ এটাই ছিল। ──
    var ufNorm=function(s){return (s||"").toString().toLowerCase().replace(/[^a-z0-9]/g,"");};
    var uHdrNorm=uRows[0].map(function(h){return ufNorm(h);});
    var idC=uHdr.indexOf("id"); if(idC===-1)idC=uHdr.indexOf("phone");
    var uTopicIdC=uHdr.indexOf("topic_id");  // ── dirty-tracking-এর জন্য ──
    var fld=(e.parameter.field||"").toLowerCase().trim();
    var fldNorm=ufNorm(fld);
    // opt1→Opt1, opt2→Opt2 etc. মিল normalized indexOf দিয়েই প্রথমে ট্রাই
    var fldC=uHdrNorm.indexOf(fldNorm);
    if(fldC===-1){
      // try "opt1" → look for "opt1" OR "option1" columns
      var altMap={"opt1":["opt1","option1"],"opt2":["opt2","option2"],"opt3":["opt3","option3"],"opt4":["opt4","option4"]};
      if(altMap[fld]){
        for(var ai=0;ai<altMap[fld].length;ai++){fldC=uHdrNorm.indexOf(ufNorm(altMap[fld][ai]));if(fldC!==-1)break;}
      }
    }
    if(fldC===-1){for(var fc=0;fc<uHdrNorm.length;fc++){if(uHdrNorm[fc].indexOf(fldNorm)!==-1){fldC=fc;break;}}}
    // ⚠️ Study ট্যাবের আসল হেডার "sub_topic"/"subtopic" না, "topic" — normalized
    // ম্যাচেও সেটা ধরা যায় না (কারণ "topic" আর "subtopic" সম্পূর্ণ আলাদা শব্দ)।
    // fld যদি sub_topic/subtopic-জাতীয় কিছু হয় আর কলাম না পাওয়া যায়, "topic"
    // কলাম ট্রাই করা হচ্ছে — নইলে Admin App থেকে Study-র sub-topic এডিট করলে
    // "Column not found" এরর দিত।
    if(fldC===-1 && (fld==="sub_topic"||fld==="subtopic")) fldC=uHdrNorm.indexOf("topic");
    if(idC===-1||fldC===-1)return json({result:"error",error:"Column not found: "+fld});
    var targetId=(e.parameter.id||"").toString().trim();
    var content=(e.parameter.content||"");
    var ufAtC=uHdrNorm.indexOf("updatedat");
    // ── 🆕 Added by / Edited by / Review — কে/কখন/কী বদলালো তার audit trail।
    // editSource না পাঠালে ডিফল্ট "Admin App" (এখন পর্যন্ত updateField শুধু Admin
    // App থেকেই কল হয়) — Main Smart Study App থেকে কোনোদিন এই একই endpoint
    // কল হলে "Main App" পাঠালেই যথেষ্ট, বাকিটা এমনিই কাজ করবে। এডিট-কলামগুলো
    // (edited_by/review) sheet-এ না থাকলে চুপচাপ স্কিপ হয়ে যাবে (ক্ষতি নেই),
    // তাই বাকি সব এডিট আগের মতোই কাজ করবে যতক্ষণ না কলাম দুটো ম্যানুয়ালি
    // Quiz/QBank/Study শিটে যোগ করা হচ্ছে। ──
    var editSource=(e.parameter.editSource||"Admin App").toString().trim();
    var editedByC=uHdrNorm.indexOf("editedby");
    var reviewC=uHdrNorm.indexOf("review");
    var reviewLabel=reviewLabelForField(fld);
    for(var ur=1;ur<uRows.length;ur++){
      if(uRows[ur][idC].toString().trim()===targetId){
        uSheet.getRange(ur+1,fldC+1).setValue(content);
        if(ufAtC!==-1) uSheet.getRange(ur+1,ufAtC+1).setValue(Date.now());
        if(editedByC!==-1) uSheet.getRange(ur+1,editedByC+1).setValue(editSource+" - "+new Date().toLocaleString('bn-BD'));
        if(reviewC!==-1 && reviewLabel){
          var prevReview=(uRows[ur][reviewC]||"").toString().trim();
          var nextReview=prevReview?(prevReview+", "+reviewLabel):reviewLabel;
          uSheet.getRange(ur+1,reviewC+1).setValue(nextReview);
        }
        syncToFirebase(shName,shName);
        // 🐛 ফিক্স (Admin App audit-এ পাওয়া): field নিজেই "topic_id" হলে
        // pre-write snapshot (uRows[ur][uTopicIdC]) ব্যবহার করলে *নতুন* topic_id
        // কখনো dirty মার্ক হতো না (পুরনো/ফাঁকা মান মার্ক হতো) — ReviewTab-এর
        // parallel ৪-field subject/topic-classify ফ্লো-তে (syncFieldsToSheet)
        // এটাই ছিল যেই একটা কল আসলে topic_id বসাচ্ছে সেটাই ভুল/কোনো dirty-মার্ক
        // না করার কারণ। এখন field===topic_id হলে সদ্য-লেখা content (নতুন মান)
        // ব্যবহার হয়, নাহলে আগের মতোই snapshot-এর মান।
        var uDirtyTopicId = (uTopicIdC>=0 && fldC===uTopicIdC) ? content.toString() : (uTopicIdC>=0 ? (uRows[ur][uTopicIdC]||"").toString() : "");
        if (uDirtyTopicId) markTopicDirty(uDirtyTopicId);
        // ── FIX: update_fields (plural, POST)-এর মতোই একই গ্যাপ এই singular
        // GET action=updateField-এও ছিল — topic_id বদলালে dirty মার্ক হতো,
        // কিন্তু reindex flag কখনো উঠতো না, তাই row_start/row_count grouping
        // পরের mutation না আসা পর্যন্ত stale থেকে যেত। ──
        if (uTopicIdC>=0 && fldC===uTopicIdC) markReindexNeeded_();
        return json({result:"success"});
      }
    }
    return json({result:"error",error:"ID not found: "+targetId});
    });
  }

  // ── changePassword ──
  if (action==="changePassword") {
    var ss=SpreadsheetApp.getActiveSpreadsheet();
    var phone=(e.parameter.phone||"").toString().trim();
    var newPass=(e.parameter.newPassword||"").toString().trim();
    if(!phone||!newPass)return json({result:"error",error:"phone or newPassword missing"});
    var uSh=ss.getSheetByName("Users"); if(!uSh)return json({result:"error",error:"Users sheet not found"});
    var uData=uSh.getDataRange().getValues();
    var uHdr=uData[0].map(function(h){return h.toString().toLowerCase().trim();});
    var phCol=uHdr.indexOf("phone"), pwCol=uHdr.indexOf("password");
    if(phCol===-1||pwCol===-1)return json({result:"error",error:"phone/password column not found"});
    var normPhone=phone.replace(/^0+/,'');
    for(var i=1;i<uData.length;i++){
      var rowPhone=uData[i][phCol].toString().trim().replace(/^0+/,'');
      if(rowPhone===normPhone){
        var hashedNew=hashPassword(newPass);
        uSh.getRange(i+1,pwCol+1).setValue(hashedNew);
        // Also sync to Firebase
        syncToFirebase("Users","Users");
        return json({result:"success",hashed:hashedNew});
      }
    }
    return json({result:"error",error:"User not found: "+phone});
  }

  // ── activateUser ──
  if (action==="activateUser") {
    var ss=SpreadsheetApp.getActiveSpreadsheet();
    var phone=(e.parameter.phone||"").toString().trim();
    if(!phone)return json({result:"error",error:"phone missing"});
    var uSh=ss.getSheetByName("Users"); if(!uSh)return json({result:"error",error:"Users sheet not found"});
    var uData=uSh.getDataRange().getValues();
    var uHdr=uData[0].map(function(h){return h.toString().toLowerCase().trim();});
    var phCol=uHdr.indexOf("phone"), stCol=uHdr.indexOf("status");
    if(stCol===-1){stCol=uData[0].length;uSh.getRange(1,stCol+1).setValue("Status");}
    var normPhone=phone.replace(/^0+/,'');
    for(var i=1;i<uData.length;i++){
      var rowPhone=(phCol!==-1?uData[i][phCol]:"").toString().trim().replace(/^'+/,'').replace(/^0+/,'');
      if(rowPhone===normPhone||uData[i][phCol].toString().trim()===phone){
        uSh.getRange(i+1,stCol+1).setValue("Active");
        syncToFirebase("Users","Users");
        var fcmResult=sendFCMToPhone(phone,"🎉 অ্যাকাউন্ট অ্যাক্টিভ!","Smart Study-তে স্বাগতম!",{type:"account_activated"});
        return json({result:"success",fcm:fcmResult});
      }
    }
    return json({result:"error",error:"User not found: "+phone});
  }

  // ── deleteReport ── ★ key OR reportKey দুটোই support করে
  if (action==="deleteReport") {
    var key=(e.parameter.key||e.parameter.reportKey||"").toString().trim();
    if(!key)return json({result:"error",error:"key missing"});
    // Firebase delete
    try{UrlFetchApp.fetch(cfg.FIREBASE_URL+"Reports/"+key+".json?auth="+cfg.SECRET_KEY,{method:"delete",muteHttpExceptions:true});}catch(fe){Logger.log("FB delete error: "+fe);}
    // Sheet delete
    var ss2=SpreadsheetApp.getActiveSpreadsheet(), rs=ss2.getSheetByName("Reports");
    if(rs){
      var rowNum=-1;
      if(key.indexOf("row_")===0)rowNum=parseInt(key.replace("row_",""),10);
      else if(!isNaN(parseInt(key,10)))rowNum=parseInt(key,10);
      if(rowNum>=1&&rowNum<rs.getLastRow())rs.deleteRow(rowNum+1);
    }
    try{syncToFirebase("Reports","Reports");}catch(_){}
    return json({result:"success",key:key});
  }

  // ── renameField ── ★ subject/topic/sub_topic cascade rename across entire sheet
  if (action==="renameField") {
    return withWriteLock(function(){
    var shName=e.parameter.sheet||"QBank";
    var shMap2={quiz:"Quiz",qbank:"QBank",study:"Study"};
    shName=shMap2[shName.toLowerCase()]||shName;
    var field=(e.parameter.field||"subject");
    // 🐛 ফিক্স: আগে শুধু .trim() দিয়ে ম্যাচ হতো — invisible zero-width char/nbsp থাকলে
    // (যেমন taxonomy-র পুরনো বাগে ঢুকে যাওয়া \u200b) সেই variant কখনো ম্যাচ হতো না,
    // আলাদা "ভুতুড়ে" ডুপ্লিকেট থেকে যেত। এখন normalizeFieldValue_ দিয়ে ম্যাচ হয়, তাই
    // দেখতে-একই-রকম সব variant (invisible char যাই থাকুক) একবারেই merge হয়ে যায়।
    var oldV=normalizeFieldValue_(e.parameter.oldVal||e.parameter.old||"");
    var newV=(e.parameter.newVal||e.parameter.new||"").trim();
    if(!oldV||!newV)return json({result:"error",error:"missing values"});
    var ss3=SpreadsheetApp.getActiveSpreadsheet(), sh3=ss3.getSheetByName(shName);
    if(!sh3)return json({result:"error",error:"sheet not found: "+shName});
    var d3=sh3.getDataRange().getValues(), h3=d3[0];
    // Find column — support subject/topic/sub_topic
    var fIdx=-1;
    for(var fi=0;fi<h3.length;fi++){
      if(h3[fi].toString().toLowerCase().trim()===field.toLowerCase().trim()){fIdx=fi;break;}
    }
    // ⚠️ Study ট্যাবের আসল হেডার "sub_topic" না, "topic" — field==="sub_topic" পাঠানো
    // হলে (renameSubjectOrTopic থেকে) সরাসরি ম্যাচ না পেলে "topic" কলাম ট্রাই করা হয়,
    // নইলে Study sub-topic rename সবসময় "field not found: sub_topic" দিত।
    if(fIdx<0 && field.toLowerCase().trim()==="sub_topic"){
      for(var fi2=0;fi2<h3.length;fi2++){
        if(h3[fi2].toString().toLowerCase().trim()==="topic"){fIdx=fi2;break;}
      }
    }
    if(fIdx<0)return json({result:"error",error:"field not found: "+field});

    // Firebase mirror sync-এর জন্য দরকার — updateField-এর মতোই id/updatedAt কলাম বের করা হচ্ছে
    var updColIdx2=-1, idColIdx2=-1, rfTopicIdC=-1;
    for(var uc=0;uc<h3.length;uc++){
      var un=h3[uc].toString().toLowerCase().replace(/\s+/g,"");
      if(un==="updatedat")updColIdx2=uc;
      if(un==="id")idColIdx2=uc;
      if(un==="topicid")rfTopicIdC=uc;   // ── dirty-tracking-এর জন্য ──
    }
    var rfDirty={};   // ── touched হওয়া সব distinct topic_id (dirty-tracking) ──

    var count=0, nowMs=Date.now(), touchedRows=[];
    for(var i3=1;i3<d3.length;i3++){
      if(normalizeFieldValue_(d3[i3][fIdx])===oldV){
        sh3.getRange(i3+1,fIdx+1).setValue(newV);
        if(updColIdx2!==-1) sh3.getRange(i3+1,updColIdx2+1).setValue(nowMs);
        if(rfTopicIdC>=0) rfDirty[(d3[i3][rfTopicIdC]||"").toString()]=1;
        touchedRows.push(i3+1);
        count++;
      }
    }
    // For topic rename: also update sub_topic column if it starts with "oldV > ..." (normalized match)
    if(field.toLowerCase()==="topic"||field.toLowerCase()==="sub_topic"){
      var stIdx=-1;
      for(var si=0;si<h3.length;si++){var sn=h3[si].toString().toLowerCase().replace(/\s+/g,"");if(sn==="sub_topic"||sn==="subtopic"){stIdx=si;break;}}
      if(stIdx!==-1 && stIdx!==fIdx){
        for(var i4=1;i4<d3.length;i4++){
          var stVal=normalizeFieldValue_(d3[i4][stIdx]);
          if(stVal.indexOf(oldV+" > ")===0){
            sh3.getRange(i4+1,stIdx+1).setValue(newV+" > "+stVal.substring(oldV.length+3));
            if(updColIdx2!==-1) sh3.getRange(i4+1,updColIdx2+1).setValue(nowMs);
            if(rfTopicIdC>=0) rfDirty[(d3[i4][rfTopicIdC]||"").toString()]=1;
            if(touchedRows.indexOf(i4+1)===-1) touchedRows.push(i4+1);
            count++;
          }
        }
      }
    }

    // ── আগে এখানে Firebase sync ইচ্ছাকৃতভাবে স্কিপ করা হতো ("app থেকেই আলাদা Firebase
    // patch হয়ে যায়" ধরে নিয়ে) — কিন্তু এখন renameField-ই একমাত্র জায়গা যেখান থেকে Sheet
    // rename হয়, তাই এখানেই Firebase mirror sync করে দেওয়া হচ্ছে (updateField-এর প্যাটার্ন
    // অনুসরণ করে — শুধু touched row-গুলোর updatedAt বসিয়ে syncToFirebase-কে incremental
    // patch করতে দেওয়া হয়, পুরো sheet re-upload হয় না)। ──
    var fbSynced=true;
    if(idColIdx2!==-1 && updColIdx2!==-1 && touchedRows.length){
      fbSynced=syncToFirebase(shName, shName);
    }
    markTopicsDirty(rfDirty);

    return json({result:"success",count:count,field:field,old:oldV,new:newV,firebaseSynced:fbSynced});
    });
  }

  /* ══════════════════════════════════════════════════════════
     PHASE 3 — নতুন schema (v2) actions
     Subjects/Topics/Tags/Posts/Institutions reference-টেবিল
     ও paginated question-fetch এর জন্য
  ══════════════════════════════════════════════════════════ */

  // ── REF_TABS: reference-টেবিলের নাম ও তাদের id-কলাম ──
  var REF_TABS = {
    subjects:     {sheet:"Subjects",     idCol:"subject_id",   nameCol:"subject_name"},
    topics:       {sheet:"Topics",       idCol:"topic_id",     nameCol:"topic_name"},
    tags:         {sheet:"Tags",         idCol:"tag_id",       nameCol:"tag_name"},
    posts:        {sheet:"Posts",        idCol:"post_id",      nameCol:"post_name"},
    institutions: {sheet:"Institutions", idCol:"institution_id", nameCol:"institution_name"}
  };

  // ── resolveOrCreateSubjectTopicId — যেসব automation-এ (যেমন QBank→Quiz
  // converter) শুধু subject/topic-এর নাম আসে, কোনো id আসে না, সেখান থেকে সরাসরি
  // id resolve/create করার জন্য। addReferenceItem-এর সাথে হুবহু একই ID-কনভেনশন
  // মেনে চলে (Quiz→QZ_S, QBank→QB, Study→ST_S; topic সবসময় subject_id+"_T"+
  // সিরিয়াল) — নাম মিলিয়ে (case/space-insensitive) বিদ্যমান subject/topic থাকলে
  // সেটাই রিইউজ করে, না থাকলে নতুন তৈরি করে। batchCache দিলে (একই ব্যাচে বারবার
  // sheet না পড়ে) in-memory-তেই নতুন তৈরি হওয়া entry গুলো cache থাকে। ──
  function resolveOrCreateSubjectTopicId(sheetName, subjectName, topicName, batchCache){
    subjectName=(subjectName||"").toString().trim();
    topicName=(topicName||"").toString().trim();
    if(!subjectName) return{subjectId:"",topicId:""};
    var rstNorm=function(s){return (s||"").toString().trim().toLowerCase().replace(/\s+/g," ");};
    var rstSs=SpreadsheetApp.getActiveSpreadsheet();
    var cache=batchCache||{};

    if(!cache._subjSh){
      cache._subjSh=rstSs.getSheetByName("Subjects");
      cache._subjData=cache._subjSh.getDataRange().getValues();
      cache._subjHdr=cache._subjData[0];
      cache._subjIdCol=cache._subjHdr.indexOf("subject_id");
      cache._subjNameCol=cache._subjHdr.indexOf("subject_name");
      cache._subjSheetCol=cache._subjHdr.indexOf("sheet");
    }
    if(!cache._topicSh){
      cache._topicSh=rstSs.getSheetByName("Topics");
      cache._topicData=cache._topicSh.getDataRange().getValues();
      cache._topicHdr=cache._topicData[0];
      cache._topicIdCol=cache._topicHdr.indexOf("topic_id");
      cache._topicNameCol=cache._topicHdr.indexOf("topic_name");
      cache._topicSubCol=cache._topicHdr.indexOf("subject_id");
    }

    var subjectId="";
    for(var rs=1;rs<cache._subjData.length;rs++){
      if(cache._subjData[rs][cache._subjSheetCol]===sheetName && rstNorm(cache._subjData[rs][cache._subjNameCol])===rstNorm(subjectName)){
        subjectId=(cache._subjData[rs][cache._subjIdCol]||"").toString(); break;
      }
    }
    if(!subjectId){
      var rstPrefix=(sheetName==="Quiz"?"QZ_S":sheetName==="QBank"?"QB":"ST_S");
      var rstMax=0;
      for(var rs2=1;rs2<cache._subjData.length;rs2++){
        var rsId=(cache._subjData[rs2][cache._subjIdCol]||"").toString();
        if(rsId.indexOf(rstPrefix)===0){ var rsN=parseInt(rsId.substring(rstPrefix.length),10); if(!isNaN(rsN)&&rsN>rstMax) rstMax=rsN; }
      }
      subjectId=rstPrefix+(rstMax+1<10?"0"+(rstMax+1):(rstMax+1));
      var rstNewSubjRow=new Array(cache._subjHdr.length).fill("");
      rstNewSubjRow[cache._subjIdCol]=subjectId; rstNewSubjRow[cache._subjNameCol]=subjectName; rstNewSubjRow[cache._subjSheetCol]=sheetName;
      cache._subjSh.appendRow(rstNewSubjRow);
      cache._subjData.push(rstNewSubjRow); // in-memory cache-ও আপডেট, একই ব্যাচে আবার লাগলে সেভ হওয়া রো-ই রিইউজ হবে
    }

    if(!topicName) return{subjectId:subjectId,topicId:""};

    var topicId="";
    for(var rt=1;rt<cache._topicData.length;rt++){
      if((cache._topicData[rt][cache._topicSubCol]||"").toString()===subjectId && rstNorm(cache._topicData[rt][cache._topicNameCol])===rstNorm(topicName)){
        topicId=(cache._topicData[rt][cache._topicIdCol]||"").toString(); break;
      }
    }
    if(!topicId){
      var rstTPrefix=subjectId+"_T";
      var rstTMax=0;
      for(var rt2=1;rt2<cache._topicData.length;rt2++){
        var rtId=(cache._topicData[rt2][cache._topicIdCol]||"").toString();
        if(rtId.indexOf(rstTPrefix)===0){ var rtN=parseInt(rtId.substring(rstTPrefix.length),10); if(!isNaN(rtN)&&rtN>rstTMax) rstTMax=rtN; }
      }
      topicId=rstTPrefix+(rstTMax+1<10?"0"+(rstTMax+1):(rstTMax+1));
      var rstNewTopicRow=new Array(cache._topicHdr.length).fill("");
      rstNewTopicRow[cache._topicIdCol]=topicId; rstNewTopicRow[cache._topicNameCol]=topicName; rstNewTopicRow[cache._topicSubCol]=subjectId;
      cache._topicSh.appendRow(rstNewTopicRow);
      cache._topicData.push(rstNewTopicRow);
    }
    return{subjectId:subjectId,topicId:topicId};
  }

  // ── getReferenceData — Subjects/Topics/Tags/Posts/Institutions
  // সবগুলো ছোট রেফারেন্স-টেবিল একবারে fetch করে (এগুলো ছোট বলেই বাল্ক-ফেচ
  // নিরাপদ — এখানেই একমাত্র জায়গা যেখানে ছোট টেবিলের পুরোটা একসাথে পাঠানো হয়) ──
  if (action==="getReferenceData") {
    var refSs=SpreadsheetApp.getActiveSpreadsheet();
    var refOut={};
    for (var refKey in REF_TABS) {
      var refCfg=REF_TABS[refKey];
      var refSh=refSs.getSheetByName(refCfg.sheet);
      refOut[refKey]=[];
      if (refSh && refSh.getLastRow()>=2) {
        var refData=refSh.getDataRange().getValues();
        var refHdr=refData[0];
        for (var ri=1;ri<refData.length;ri++) {
          var refRec={};
          for (var rj=0;rj<refHdr.length;rj++) {
            var rk=refHdr[rj].toString().trim();
            if (!rk) continue;
            refRec[rk]=refData[ri][rj];
          }
          if (refRec[refCfg.idCol]) refOut[refKey].push(refRec);
        }
      }
    }
    return json({status:"success",result:"success",data:refOut});
  }

  // ── renameReferenceItem — subject/topic/tag/post/institution rename।
  // আগের renameField-এর মতো পুরো Quiz/QBank/Study স্ক্যান করে না — শুধু
  // সংশ্লিষ্ট reference ট্যাবের ১টা রো (id দিয়ে খুঁজে) বদলায়। questions
  // subject_id/topic_id দিয়ে reference করে বলে rename-এ তাদের কিছুই ছোঁয়া
  // লাগে না — এটাই মূল fix যেটা বহুবার আলোচনা হয়েছে। ──
  if (action==="renameReferenceItem") {
    return withWriteLock(function(){
    var rriType=(e.parameter.refType||"").toLowerCase();
    var rriId=(e.parameter.id||"").toString().trim();
    var rriNewName=(e.parameter.newName||"").toString().trim();
    var rriCfg=REF_TABS[rriType];
    if (!rriCfg) return json({status:"error",result:"error",message:"অজানা refType: "+rriType});
    if (!rriId || !rriNewName) return json({status:"error",result:"error",message:"id/newName missing"});
    var rriSs=SpreadsheetApp.getActiveSpreadsheet(), rriSh=rriSs.getSheetByName(rriCfg.sheet);
    if (!rriSh) return json({status:"error",result:"error",message:"Sheet not found: "+rriCfg.sheet});
    var rriData=rriSh.getDataRange().getValues(), rriHdr=rriData[0];
    var rriIdCol=-1, rriNameCol=-1;
    for (var rc=0;rc<rriHdr.length;rc++){
      var rcName=rriHdr[rc].toString().trim();
      if (rcName===rriCfg.idCol) rriIdCol=rc;
      if (rcName===rriCfg.nameCol) rriNameCol=rc;
    }
    if (rriIdCol<0||rriNameCol<0) return json({status:"error",result:"error",message:"id/name column not found in "+rriCfg.sheet});
    var rriFound=false;
    for (var rr=1;rr<rriData.length;rr++){
      if ((rriData[rr][rriIdCol]||"").toString().trim()===rriId){
        rriSh.getRange(rr+1,rriNameCol+1).setValue(rriNewName);
        rriFound=true;
        break; // ★ ঠিক ১টা রো — এখানেই cascade এড়ানো হচ্ছে
      }
    }
    if (!rriFound) return json({status:"error",result:"error",message:"id পাওয়া যায়নি: "+rriId});
    // Firebase mirror — শুধু এই ছোট reference node, প্রশ্নের কোনো node টাচ হয় না
    var rriFbOk=true;
    try {
      var rriCfgProps=getProps();
      if (rriCfgProps.FIREBASE_URL) {
        var rriUrl=rriCfgProps.FIREBASE_URL+rriCfg.sheet+"/"+encodeURIComponent(rriId)+"/"+rriCfg.nameCol+".json"
          +(rriCfgProps.FIREBASE_DB_SECRET?("?auth="+rriCfgProps.FIREBASE_DB_SECRET):"");
        UrlFetchApp.fetch(rriUrl,{method:"put",contentType:"application/json",payload:JSON.stringify(rriNewName),muteHttpExceptions:true});
      }
    } catch(rriErr){ rriFbOk=false; }

    // ── CDN dirty-tracking — cascade এড়ানো হলেও (Quiz/QBank/Study রো টাচ হয়
    // না), publish স্ক্রিপ্ট এখন subject/subTopic নাম Reference টেবিল থেকেই
    // resolve করে (Option B, GAS_CDN_PLANNING.md দেখো) — তাই rename করলে যেসব
    // Topic-এর content JSON-এ এই নাম দেখায়, সেগুলোকে dirty মার্ক করতে হবে,
    // নাহলে পুরনো নামই CDN-এ থেকে যাবে। Subject rename হলে তার আন্ডারের সব
    // Topic; Topic rename হলে শুধু সেই একটা Topic। ──
    if (rriType==="topics") {
      markTopicDirty(rriId);
    } else if (rriType==="subjects") {
      var rriTopicsSh=rriSs.getSheetByName("Topics");
      if (rriTopicsSh) {
        var rriTData=rriTopicsSh.getDataRange().getValues(), rriTHdr=rriTData[0];
        var rriTIdCol=rriTHdr.indexOf("topic_id"), rriTSubCol=rriTHdr.indexOf("subject_id");
        if (rriTIdCol>=0 && rriTSubCol>=0) {
          var rriDirty={};
          for (var rt=1;rt<rriTData.length;rt++){
            if ((rriTData[rt][rriTSubCol]||"").toString().trim()===rriId) {
              rriDirty[(rriTData[rt][rriTIdCol]||"").toString()]=1;
            }
          }
          markTopicsDirty(rriDirty);
        }
      }
    }

    return json({status:"success",result:"success",refType:rriType,id:rriId,newName:rriNewName,rowsChanged:1,firebaseSynced:rriFbOk});
    });
  }

  // ── addReferenceItem — Subjects/Topics/Tags/Posts/Institutions-এ
  // নতুন এন্ট্রি যোগ করে, id নিজে থেকে জেনারেট করে (parent-scoped prefix সহ)।
  // Manager UI থেকে "নতুন যোগ করো" বাটনে ব্যবহার হয়। ──
  if (action==="addReferenceItem") {
    return withWriteLock(function(){
    var ariType=(e.parameter.refType||"").toLowerCase();
    var ariName=(e.parameter.name||"").toString().trim();
    var ariParentId=(e.parameter.parentId||"").toString().trim(); // topics→subject_id
    var ariSheet=(e.parameter.sheet||"").toString().trim(); // শুধু subjects-এর জন্য (Quiz/QBank/Study)
    var ariCfg=REF_TABS[ariType];
    if (!ariCfg) return json({status:"error",result:"error",message:"অজানা refType: "+ariType});
    if (!ariName) return json({status:"error",result:"error",message:"name প্রয়োজন"});
    if (ariType==="topics" && !ariParentId) return json({status:"error",result:"error",message:"parentId প্রয়োজন"});
    if (ariType==="subjects" && ["Quiz","QBank","Study"].indexOf(ariSheet)<0) return json({status:"error",result:"error",message:"sheet প্রয়োজন (Quiz/QBank/Study)"});

    var ariSs=SpreadsheetApp.getActiveSpreadsheet(), ariSh=ariSs.getSheetByName(ariCfg.sheet);
    if (!ariSh) return json({status:"error",result:"error",message:"Sheet not found: "+ariCfg.sheet});
    var ariData=ariSh.getDataRange().getValues(), ariHdr=ariData[0];
    var ariIdCol=ariHdr.indexOf(ariCfg.idCol);

    // ── নতুন id জেনারেট (parent-scoped prefix + পরের সিরিয়াল নাম্বার) —
    // ⚠️ getNextId()-এর মতোই এখানেও max-scan করে পরের সিরিয়াল বের করা হয়, তাই
    // withWriteLock ছাড়া দুইটা concurrent addReferenceItem কল একই id জেনারেট
    // করে ফেলতে পারত (duplicate topic_id) — এখন lock-এর ভিতরে বলে নিরাপদ। ──
    var ariNewId="";
    if (ariType==="subjects") {
      // ⚠️ QBank-এর subject id কনভেনশন অন্য দুটোর (Quiz→QZ_S, Study→ST_S) থেকে আলাদা —
      // QBank-এ আগে থেকেই বিদ্যমান subject-গুলো "QB01".."QB09" ফরম্যাটে (আন্ডারস্কোর/S
      // ছাড়া) তৈরি হয়ে আছে। আগে এখানে ভুলবশত সব সিটেই "_S" প্যাটার্ন ব্যবহার হতো
      // (QBank-এও "QB_S01" জেনারেট হতো), যেটা বিদ্যমান কনভেনশনের সাথে না মেলায় প্রতিটা
      // নতুন Subject আলাদা/ভিন্ন id-তে চলে যেত, বিদ্যমান এন্ট্রির সাথে মিলতো না। এখন
      // QBank-এর জন্য বিদ্যমান কনভেনশন অনুসরণ করা হচ্ছে (Quiz/Study অপরিবর্তিত)। ──
      var ariPrefix=(ariSheet==="Quiz"?"QZ_S":ariSheet==="QBank"?"QB":"ST_S");
      var ariMax=0;
      for (var a1=1;a1<ariData.length;a1++){
        var aId1=(ariData[a1][ariIdCol]||"").toString();
        if (aId1.indexOf(ariPrefix)===0){ var n1=parseInt(aId1.substring(ariPrefix.length),10); if(!isNaN(n1)&&n1>ariMax) ariMax=n1; }
      }
      ariNewId=ariPrefix+(ariMax+1<10?"0"+(ariMax+1):(ariMax+1));
    } else if (ariType==="topics") {
      var ariPrefix2=ariParentId+"_T";
      var ariMax2=0;
      for (var a2=1;a2<ariData.length;a2++){
        var aId2=(ariData[a2][ariIdCol]||"").toString();
        if (aId2.indexOf(ariPrefix2)===0){ var n2=parseInt(aId2.substring(ariPrefix2.length),10); if(!isNaN(n2)&&n2>ariMax2) ariMax2=n2; }
      }
      ariNewId=ariPrefix2+(ariMax2+1<10?"0"+(ariMax2+1):(ariMax2+1));
    } else { // tags/posts/institutions — flat prefix (TAG/P/I)
      var ariPrefix4=(ariType==="tags"?"TAG":ariType==="posts"?"P":"I");
      var ariMax4=0;
      for (var a4=1;a4<ariData.length;a4++){
        var aId4=(ariData[a4][ariIdCol]||"").toString();
        if (aId4.indexOf(ariPrefix4)===0){ var n4=parseInt(aId4.substring(ariPrefix4.length),10); if(!isNaN(n4)&&n4>ariMax4) ariMax4=n4; }
      }
      ariNewId=ariPrefix4+(ariMax4+1<10?"0"+(ariMax4+1):(ariMax4+1));
    }

    // ── নতুন রো বসাও (হেডার অনুযায়ী কলাম মিলিয়ে) ──
    var ariNewRow=new Array(ariHdr.length).fill("");
    ariNewRow[ariIdCol]=ariNewId;
    var ariNameCol=ariHdr.indexOf(ariCfg.nameCol);
    if (ariNameCol>=0) ariNewRow[ariNameCol]=ariName;
    if (ariType==="subjects"){ var ariSheetCol=ariHdr.indexOf("sheet"); if(ariSheetCol>=0) ariNewRow[ariSheetCol]=ariSheet; }
    if (ariType==="topics"){ var ariSubCol=ariHdr.indexOf("subject_id"); if(ariSubCol>=0) ariNewRow[ariSubCol]=ariParentId; }
    ariSh.appendRow(ariNewRow);

    return json({status:"success",result:"success",refType:ariType,id:ariNewId,name:ariName});
    });
  }

  // ── deleteReferenceItem — একটা রেফারেন্স-এন্ট্রি ডিলিট করে (id দিয়ে)।
  // ⚠️ এটা শুধু reference টেবিলের রো মোছে — যেসব প্রশ্ন এই id ব্যবহার করছে
  // তাদের subject_id/topic_id ফাঁকা/orphan হয়ে যাবে (প্রশ্ন মোছে না)। Admin
  // UI-তে ডিলিটের আগে ব্যবহার-সংখ্যা দেখিয়ে সতর্ক করা উচিত। ──
  if (action==="deleteReferenceItem") {
    return withWriteLock(function(){
    var driType=(e.parameter.refType||"").toLowerCase();
    var driId=(e.parameter.id||"").toString().trim();
    var driCfg=REF_TABS[driType];
    if (!driCfg) return json({status:"error",result:"error",message:"অজানা refType: "+driType});
    if (!driId) return json({status:"error",result:"error",message:"id প্রয়োজন"});
    var driSs=SpreadsheetApp.getActiveSpreadsheet(), driSh=driSs.getSheetByName(driCfg.sheet);
    if (!driSh) return json({status:"error",result:"error",message:"Sheet not found: "+driCfg.sheet});
    var driData=driSh.getDataRange().getValues(), driHdr=driData[0];
    var driIdCol=driHdr.indexOf(driCfg.idCol);
    var driFound=false;
    for (var d1=driData.length-1;d1>=1;d1--){
      if ((driData[d1][driIdCol]||"").toString().trim()===driId){ driSh.deleteRow(d1+1); driFound=true; break; }
    }
    if (!driFound) return json({status:"error",result:"error",message:"id পাওয়া যায়নি: "+driId});

    // ── FIX (App feature request ৩ — QBank Admin: পদবী/প্রতিষ্ঠান Delete):
    // Post/Institution রেফারেন্স-এন্ট্রি ডিলিট হলে Exam_Appearances-এর সংশ্লিষ্ট
    // রো-ও cascade-delete করা হয় (নাহলে ওই appearance-গুলো orphan post_id/
    // institution_id নিয়ে পড়ে থাকত, "পদবী/প্রতিষ্ঠান-মোডে" প্রশ্ন গণনা ভুল দেখাত)।
    // মূল প্রশ্ন (Quiz/QBank/Study রো) কখনোই টাচ হয় না — শুধু appearance-লিংক মোছে। ──
    var driEaDeleted=0;
    if (driType==="posts" || driType==="institutions") {
      var driEaSh=driSs.getSheetByName("Exam_Appearances");
      if (driEaSh && driEaSh.getLastRow()>=2) {
        var driEaData=driEaSh.getDataRange().getValues(), driEaHdr=driEaData[0];
        var driEaCol=driEaHdr.indexOf(driType==="posts"?"post_id":"institution_id");
        if (driEaCol>=0) {
          for (var de1=driEaData.length-1; de1>=1; de1--){
            if ((driEaData[de1][driEaCol]||"").toString().trim()===driId){
              driEaSh.deleteRow(de1+1); driEaDeleted++;
            }
          }
        }
      }
    }
    return json({status:"success",result:"success",refType:driType,id:driId,deleted:1,appearancesDeleted:driEaDeleted});
    });
  }

  // ── updateReferenceField — Subjects/Topics/Tags/Posts/Institutions-এর যেকোনো
  // একটা কলাম (নাম-কলাম ছাড়া অন্য যেকোনো, যেমন "emoji") সেট করে, id দিয়ে খুঁজে।
  // App feature request ৪ — এডমিন সাবজেক্ট/টপিক/পদবী/প্রতিষ্ঠানের ইমুজি বদলাতে
  // পারবে। কলামটা শিটে না থাকলে নিজে থেকেই নতুন হেডার কলাম যোগ করে নেয় (তাই
  // ম্যানুয়ালি শিটে "emoji" কলাম বানিয়ে রাখার দরকার নেই)। ──
  if (action==="updateReferenceField") {
    return withWriteLock(function(){
    var urfType=(e.parameter.refType||"").toLowerCase();
    var urfId=(e.parameter.id||"").toString().trim();
    var urfField=(e.parameter.field||"").toString().trim();
    var urfValue=(e.parameter.value||"").toString();
    var urfCfg=REF_TABS[urfType];
    if (!urfCfg) return json({status:"error",result:"error",message:"অজানা refType: "+urfType});
    if (!urfId || !urfField) return json({status:"error",result:"error",message:"id/field প্রয়োজন"});
    if (urfField===urfCfg.idCol || urfField===urfCfg.nameCol) {
      return json({status:"error",result:"error",message:"id/name কলাম এখান থেকে বদলানো যাবে না"});
    }
    var urfSs=SpreadsheetApp.getActiveSpreadsheet(), urfSh=urfSs.getSheetByName(urfCfg.sheet);
    if (!urfSh) return json({status:"error",result:"error",message:"Sheet not found: "+urfCfg.sheet});
    var urfLastCol=urfSh.getLastColumn();
    var urfHdr=urfSh.getRange(1,1,1,urfLastCol).getValues()[0];
    var urfFieldCol=-1;
    for (var uc=0;uc<urfHdr.length;uc++){
      if (urfHdr[uc].toString().trim()===urfField) { urfFieldCol=uc; break; }
    }
    if (urfFieldCol<0) {
      // কলাম নেই — শেষে নতুন কলাম যোগ করো (হেডার বসিয়ে)
      urfFieldCol=urfLastCol;
      urfSh.getRange(1,urfFieldCol+1).setValue(urfField);
    }
    var urfIdColIdx=urfHdr.indexOf(urfCfg.idCol);
    if (urfIdColIdx<0) return json({status:"error",result:"error",message:"id column not found in "+urfCfg.sheet});
    var urfLastRow=urfSh.getLastRow();
    if (urfLastRow<2) return json({status:"error",result:"error",message:"শিট খালি"});
    var urfIds=urfSh.getRange(2,urfIdColIdx+1,urfLastRow-1,1).getValues();
    var urfFound=false;
    for (var ur=0;ur<urfIds.length;ur++){
      if ((urfIds[ur][0]||"").toString().trim()===urfId){
        urfSh.getRange(ur+2,urfFieldCol+1).setValue(urfValue);
        urfFound=true;
        break;
      }
    }
    if (!urfFound) return json({status:"error",result:"error",message:"id পাওয়া যায়নি: "+urfId});
    return json({status:"success",result:"success",refType:urfType,id:urfId,field:urfField,value:urfValue});
    });
  }

  // ── mergeReferenceItem — App feature request ৩ (QBank Admin "Move"): একটা
  // Post/Institution-কে আরেকটার ভেতরে merge করে — Exam_Appearances-এর সব
  // matching রো-র post_id/institution_id fromId থেকে toId-তে বদলে দেয়, তারপর
  // fromId-এর reference-এন্ট্রি ডিলিট করে (Subject/SubTopic "move"-এর প্যাটার্নেই,
  // যেখানে একই নামের destination থাকলে auto-merge হয়)। মূল প্রশ্ন কখনো টাচ হয় না। ──
  if (action==="mergeReferenceItem") {
    return withWriteLock(function(){
    var mriType=(e.parameter.refType||"").toLowerCase();
    var mriFromId=(e.parameter.fromId||"").toString().trim();
    var mriToId=(e.parameter.toId||"").toString().trim();
    if (mriType!=="posts" && mriType!=="institutions") {
      return json({status:"error",result:"error",message:"refType শুধু posts/institutions হতে পারে"});
    }
    if (!mriFromId || !mriToId) return json({status:"error",result:"error",message:"fromId/toId প্রয়োজন"});
    if (mriFromId===mriToId) return json({status:"error",result:"error",message:"fromId ও toId একই হতে পারবে না"});
    var mriSs=SpreadsheetApp.getActiveSpreadsheet();
    var mriEaSh=mriSs.getSheetByName("Exam_Appearances");
    var mriMoved=0;
    if (mriEaSh && mriEaSh.getLastRow()>=2) {
      var mriEaData=mriEaSh.getDataRange().getValues(), mriEaHdr=mriEaData[0];
      var mriEaCol=mriEaHdr.indexOf(mriType==="posts"?"post_id":"institution_id");
      if (mriEaCol>=0) {
        for (var me=1;me<mriEaData.length;me++){
          if ((mriEaData[me][mriEaCol]||"").toString().trim()===mriFromId){
            mriEaSh.getRange(me+1,mriEaCol+1).setValue(mriToId);
            mriMoved++;
          }
        }
      }
    }
    // ── from-side reference row ডিলিট (এখন আর কোনো appearance এটা পয়েন্ট করে না) ──
    var mriCfg=REF_TABS[mriType];
    var mriRefSh=mriSs.getSheetByName(mriCfg.sheet);
    if (mriRefSh && mriRefSh.getLastRow()>=2) {
      var mriRefData=mriRefSh.getDataRange().getValues(), mriRefHdr=mriRefData[0];
      var mriRefIdCol=mriRefHdr.indexOf(mriCfg.idCol);
      for (var mr=mriRefData.length-1; mr>=1; mr--){
        if ((mriRefData[mr][mriRefIdCol]||"").toString().trim()===mriFromId){ mriRefSh.deleteRow(mr+1); break; }
      }
    }
    return json({status:"success",result:"success",refType:mriType,fromId:mriFromId,toId:mriToId,rowsMoved:mriMoved});
    });
  }

  // ── renameQBankYear — App feature request ৩: QBank "সাল" আসলে Exam_Appearances-এর
  // কলাম না — এটা সরাসরি QBank শিটের প্রতিটা প্রশ্ন-রো-এর নিজস্ব "year" কলাম (দেখো
  // QuestionItem.year — "QBank only")। তাই rename মানে: QBank শিটে যেসব রো-র year==
  // oldYear তাদের সবার year কলাম newYear-এ বদলে দেওয়া (bulk field-update, ঠিক
  // renameField action-এর মতোই, কিন্তু sheet সবসময় QBank + field সবসময় year এ ফিক্সড
  // রাখা হলো যাতে Android থেকে ভুল sheet/field পাঠানোর ঝুঁকি না থাকে)। ──
  if (action==="renameQBankYear") {
    return withWriteLock(function(){
    var ryOld=(e.parameter.oldYear||"").toString().trim();
    var ryNew=(e.parameter.newYear||"").toString().trim();
    if (!ryOld || !ryNew) return json({status:"error",result:"error",message:"oldYear/newYear প্রয়োজন"});
    var rySs=SpreadsheetApp.getActiveSpreadsheet(), ryQbSh=rySs.getSheetByName("QBank");
    if (!ryQbSh || ryQbSh.getLastRow()<2) return json({status:"error",result:"error",message:"QBank sheet খালি"});
    var ryData=ryQbSh.getDataRange().getValues(), ryHdr=ryData[0];
    var ryYearCol=ryHdr.indexOf("year");
    if (ryYearCol<0) return json({status:"error",result:"error",message:"QBank শিটে year কলাম নেই"});
    var ryCount=0;
    for (var ry=1;ry<ryData.length;ry++){
      if ((ryData[ry][ryYearCol]||"").toString().trim()===ryOld){
        ryQbSh.getRange(ry+1,ryYearCol+1).setValue(ryNew);
        ryCount++;
      }
    }
    return json({status:"success",result:"success",oldYear:ryOld,newYear:ryNew,rowsChanged:ryCount});
    });
  }


  // ── rebuildIndex — Quiz/QBank/Study প্রতিটাকে subject_id (তারপর topic_id)
  // অনুযায়ী সাজায়, আর Topics ট্যাবে row_start/row_count বসিয়ে দেয়।
  // getQuestionsPage এই index ব্যবহার করে O(limit) সময়ে পেজ ফেরত দেয়,
  // পুরো শিট স্ক্যান করা লাগে না। ⚠️ এটা ম্যানুয়ালি/ট্রিগার দিয়ে চালাতে হবে
  // (bulk add/rename এর পরে) — প্রতিটা ছোট এডিটে না, কারণ পুরো শিট re-sort
  // করে বলে খরচ আছে (কিন্তু এটা batch অপারেশন, admin-triggered, ইউজার-facing
  // read খরচের সাথে সম্পর্কহীন)। ──
  //
  // ── AUTO-REINDEX (FIX "রিইনডেক্স ভুলে যাওয়া লাগে না"): এই লজিকটা এখন
  // runRebuildIndexCore()-এ বের করে আনা হলো যাতে moveQuestions/moveTopic/
  // deleteByIds/deleteByReferenceId — এই চারটা action, যেগুলো টপিকের প্রশ্ন-
  // সংখ্যা বদলে দেয়, তারা প্রতিটাই নিজে থেকে (কারো মনে রাখা ছাড়াই) শেষে এটা
  // কল করে row_count/row_start সবসময় সঠিক রাখে। এছাড়াও নিচে
  // installAutoReindexTrigger() দিয়ে একটা পর্যায়ক্রমিক (safety-net) টাইম-
  // ট্রিগার বসানো যায় — single-question add/edit endpoint (doPost, "type"
  // ভিত্তিক, action ভিত্তিক না) কোনো dirty-marking করে না বলে সেটার জন্য এই
  // নিরাপত্তা-জাল দরকার। ──
  if (action==="rebuildIndex") {
    var ribOut=runRebuildIndexCore();
    return json({status:"success",result:"success",message:"Index rebuilt (per-sheet)",details:ribOut});
  }

  // ── getQuestionsPage — subject_id(+topic_id) অনুযায়ী ঠিক ৫০টা (বা limit)
  // প্রশ্ন ফেরত দেয়। rebuildIndex-এ বানানো row_start/row_count থাকলে সরাসরি সেই
  // row-range পড়ে (fast path, পুরো শিট স্ক্যান লাগে না)।
  // ⚠️ FIX (bug: টপিকের ভিতর প্রশ্ন 0 দেখাতো): আগে row_start/row_count Topics
  // ট্যাবে ফাঁকা/অনুপস্থিত থাকলে (rebuildIndex কখনো চালানো হয়নি, বা তারপর নতুন
  // টপিক/প্রশ্ন যোগ হয়েছে, বা কোনো প্রশ্নের topic_id ভুল/ফাঁকা থাকায় rebuildIndex
  // সেই topic_id-এর জন্য কোনো contiguous group-ই খুঁজে পায়নি) এই ফাংশন চুপচাপ
  // rows:[] (০টা প্রশ্ন) রিটার্ন করে দিতো — অথচ Subjects/Topics লিস্ট আলাদা
  // getReferenceData থেকে আসে বলে Subject/Topic নাম ঠিকই দেখাতো, শুধু ভিতরের
  // প্রশ্নই আসতো না। এখন index না থাকলে/স্টেল হলে সরাসরি sheet-এ topic_id দিয়ে
  // লাইভ স্ক্যান করে সঠিক প্রশ্নগুলো ফেরত দেয় (fallback path, একটু ধীর কিন্তু
  // কখনো ভুলভাবে ০ দেখাবে না)। ──
  if (action==="getQuestionsPage") {
    var gqpSheet=(e.parameter.sheet||"Quiz");
    var gqpMap={quiz:"Quiz",qbank:"QBank",study:"Study"};
    gqpSheet=gqpMap[gqpSheet.toLowerCase()]||gqpSheet;
    var gqpTopicId=(e.parameter.topicId||"").toString().trim();
    var gqpCursor=parseInt(e.parameter.cursor||"0",10)||0;
    var gqpLimit=Math.min(parseInt(e.parameter.limit||"50",10)||50, 100); // safety cap
    if (!gqpTopicId) return json({status:"error",result:"error",message:"topicId প্রয়োজন"});

    var gqpSs=SpreadsheetApp.getActiveSpreadsheet();
    var gqpSh=gqpSs.getSheetByName(gqpSheet);
    if (!gqpSh) return json({status:"error",result:"error",message:"Sheet not found: "+gqpSheet});
    var gqpHdr=gqpSh.getRange(1,1,1,gqpSh.getLastColumn()).getValues()[0];
    var gqpHdrNorm=gqpHdr.map(function(h){return h.toString().trim().toLowerCase();});

    // ── index খুঁজে দেখা — থাকলে ও বৈধ হলে fast path ──
    // ⚠️ FIX: আগে generic row_start/row_count কলাম পড়া হতো, যেটা rebuildIndex-এ
    // Quiz/QBank/Study তিন শিটই শেয়ার করতো (শেষে যেটা প্রসেস হতো সেটাই টিকে থাকতো)।
    // এখন এই request যে sheet-এর (gqpSheet) জন্য, ঠিক সেই sheet-স্কোপড
    // row_start_<sheet>/row_count_<sheet> কলাম পড়া হচ্ছে — অন্য sheet-এর range
    // এখানে ভুলবশত apply হওয়ার আর সুযোগ নেই।
    var gqpEntry=null;
    var gqpTopicsSh=gqpSs.getSheetByName("Topics");
    if (gqpTopicsSh) {
      var gqpTData=gqpTopicsSh.getDataRange().getValues(), gqpTHdr=gqpTData[0]||[];
      var gqpSheetKey=gqpSheet.toLowerCase();
      var gqpTIdCol=gqpTHdr.indexOf("topic_id");
      var gqpRsCol=gqpTHdr.indexOf("row_start_"+gqpSheetKey), gqpRcCol=gqpTHdr.indexOf("row_count_"+gqpSheetKey);
      // পুরনো index (rebuildIndex আগের ভার্সনে চালানো, নতুন per-sheet কলাম এখনো নেই) হলে
      // legacy generic কলামে fallback করো, নাহলে একদম নতুন সেটআপে সবাই fallback-scan করত
      if (gqpRsCol<0 || gqpRcCol<0) { gqpRsCol=gqpTHdr.indexOf("row_start"); gqpRcCol=gqpTHdr.indexOf("row_count"); }
      if (gqpTIdCol>=0 && gqpRsCol>=0 && gqpRcCol>=0) {
        for (var g1=1;g1<gqpTData.length;g1++){
          if ((gqpTData[g1][gqpTIdCol]||"").toString()===gqpTopicId){
            var gqpS=gqpTData[g1][gqpRsCol], gqpC=gqpTData[g1][gqpRcCol];
            if (gqpS && gqpC) gqpEntry={start:gqpS,count:gqpC};
            break;
          }
        }
      }
    }

    var gqpRows=[], gqpTotal=0, gqpNextCursor=gqpCursor, gqpHasMore=false;
    var gqpFastPathFailed=false;

    if (gqpEntry) {
      // ── FAST PATH: ইনডেক্স আছে ও বৈধ — সরাসরি row-range পড়ো ──
      // ⚠️ try/catch যোগ করা হলো: index স্টেল/অন্য sheet-এর হলে (out-of-range) আগে
      // getRange() সরাসরি এরর ছুঁড়তো আর সেটাই ক্লায়েন্টে "কোনো প্রশ্ন পাওয়া যায়নি —
      // ইন্টারনেট চেক করো" হয়ে দেখাতো। এখন এমন হলে চুপচাপ live-scan fallback-এ নেমে যায়।
      try {
        var gqpReadStart=gqpEntry.start+gqpCursor;
        var gqpRemaining=gqpEntry.count-gqpCursor;
        if (gqpReadStart<1 || gqpReadStart+Math.max(gqpRemaining,0)-1>gqpSh.getLastRow()) {
          throw new Error("stale index range for sheet "+gqpSheet);
        }
        gqpTotal=gqpEntry.count;
        if (gqpRemaining>0) {
          var gqpReadCount=Math.min(gqpLimit, gqpRemaining);
          var gqpVals=gqpSh.getRange(gqpReadStart,1,gqpReadCount,gqpSh.getLastColumn()).getValues();
          for (var g2=0;g2<gqpVals.length;g2++){
            var gqpRec={};
            for (var g3=0;g3<gqpHdr.length;g3++){
              var gqpKey=gqpHdr[g3].toString().trim();
              if (!gqpKey) continue;
              var gqpVal=gqpVals[g2][g3];
              gqpRec[gqpKey]=(gqpVal instanceof Date)?Utilities.formatDate(gqpVal,"GMT+6","dd-MM-yyyy HH:mm:ss"):gqpVal;
            }
            gqpRows.push(gqpRec);
          }
          gqpNextCursor=gqpCursor+gqpReadCount;
        }
        gqpHasMore=gqpNextCursor<gqpTotal;
      } catch (gqpFastErr) {
        Logger.log("getQuestionsPage fast-path failed, falling back to live scan: "+gqpFastErr);
        gqpFastPathFailed=true;
        gqpRows=[]; gqpTotal=0; gqpNextCursor=gqpCursor; gqpHasMore=false;
      }
    }
    if (!gqpEntry || gqpFastPathFailed) {
      // ── FALLBACK PATH: ইনডেক্স নেই/স্টেল/এই topic_id-এর জন্য অনুপস্থিত —
      // sheet-এ topic_id কলাম দিয়ে সরাসরি লাইভ স্ক্যান করে ম্যাচিং রো বের করো ──
      var gqpTopicColIdx=gqpHdrNorm.indexOf("topic_id");
      if (gqpTopicColIdx<0) {
        return json({status:"error",result:"error",message:"'topic_id' কলাম নেই sheet: "+gqpSheet});
      }
      var gqpAllData=gqpSh.getDataRange().getValues();
      var gqpMatches=[];
      for (var gm=1; gm<gqpAllData.length; gm++){
        if ((gqpAllData[gm][gqpTopicColIdx]||"").toString().trim()===gqpTopicId) gqpMatches.push(gqpAllData[gm]);
      }
      gqpTotal=gqpMatches.length;
      var gqpSlice=gqpMatches.slice(gqpCursor, gqpCursor+gqpLimit);
      for (var g4=0;g4<gqpSlice.length;g4++){
        var gqpRec2={};
        for (var g5=0;g5<gqpHdr.length;g5++){
          var gqpKey2=gqpHdr[g5].toString().trim();
          if (!gqpKey2) continue;
          var gqpVal2=gqpSlice[g4][g5];
          gqpRec2[gqpKey2]=(gqpVal2 instanceof Date)?Utilities.formatDate(gqpVal2,"GMT+6","dd-MM-yyyy HH:mm:ss"):gqpVal2;
        }
        gqpRows.push(gqpRec2);
      }
      gqpNextCursor=gqpCursor+gqpSlice.length;
      gqpHasMore=gqpNextCursor<gqpTotal;
    }

    return json({status:"success",result:"success",rows:gqpRows,hasMore:gqpHasMore,nextCursor:gqpNextCursor,total:gqpTotal});
  }

  // ── getExamAppearances — একটা প্রশ্ন কোন কোন পদ/প্রতিষ্ঠান/সালে এসেছে
  // (Exam_Appearances ট্যাব থেকে question_id দিয়ে খুঁজে) ──
  if (action==="getExamAppearances") {
    var geaQid=(e.parameter.questionId||"").toString().trim();
    if (!geaQid) return json({status:"error",result:"error",message:"questionId প্রয়োজন"});
    var geaSs=SpreadsheetApp.getActiveSpreadsheet(), geaSh=geaSs.getSheetByName("Exam_Appearances");
    if (!geaSh || geaSh.getLastRow()<2) return json({status:"success",result:"success",appearances:[]});
    var geaData=geaSh.getDataRange().getValues(), geaHdr=geaData[0];
    var geaQCol=geaHdr.indexOf("question_id");
    var geaOut=[];
    for (var ge=1;ge<geaData.length;ge++){
      if ((geaData[ge][geaQCol]||"").toString().trim()===geaQid){
        var geaRec={};
        for (var gj=0;gj<geaHdr.length;gj++) geaRec[geaHdr[gj].toString().trim()]=geaData[ge][gj];
        geaOut.push(geaRec);
      }
    }
    return json({status:"success",result:"success",appearances:geaOut});
  }

  // ── getReviewProgress — Admin App-এর "রিভিউ মোড"-এর জন্য: প্রতিটা subject_id ও
  // topic_id-এ মোট কত প্রশ্ন আর তার কতগুলো reviewed — শুধু ২টা কলাম (topic_id, reviewed)
  // পড়ে অ্যাগ্রিগেট করে, পুরো রো/সব কলাম রিটার্ন করে না (হালকা, দ্রুত)। sheet প্যারামিটার
  // (Quiz/QBank/Study) দিয়ে scope করা — একবারে একটা sheet-এর progress আসে।
  if (action==="getReviewProgress") {
    var grpSheet=(e.parameter.sheet||"Quiz").toString().trim();
    var grpSs=SpreadsheetApp.getActiveSpreadsheet(), grpSh=grpSs.getSheetByName(grpSheet);
    if (!grpSh || grpSh.getLastRow()<2) return json({status:"success",result:"success",subjects:{},topics:{}});
    var grpData=grpSh.getDataRange().getValues(), grpHdr=grpData[0];
    var grpSubjCol=grpHdr.indexOf("subject_id");
    var grpTopicCol=grpHdr.indexOf("topic_id");
    var grpRevCol=grpHdr.indexOf("reviewed");
    var grpIdCol=grpHdr.indexOf("id"); if (grpIdCol<0) grpIdCol=0;
    var grpSubjects={}, grpTopics={};
    for (var gr=1;gr<grpData.length;gr++){
      if (!grpData[gr][grpIdCol]) continue; // খালি রো স্কিপ
      var grpSubjId=grpSubjCol>=0 ? (grpData[gr][grpSubjCol]||"").toString().trim() : "";
      var grpTopicId=grpTopicCol>=0 ? (grpData[gr][grpTopicCol]||"").toString().trim() : "";
      var grpIsReviewed=grpRevCol>=0 && (grpData[gr][grpRevCol]===true || grpData[gr][grpRevCol]==="true" || grpData[gr][grpRevCol]==="TRUE");
      if (grpSubjId) {
        if (!grpSubjects[grpSubjId]) grpSubjects[grpSubjId]={total:0,reviewed:0};
        grpSubjects[grpSubjId].total++;
        if (grpIsReviewed) grpSubjects[grpSubjId].reviewed++;
      }
      if (grpTopicId) {
        if (!grpTopics[grpTopicId]) grpTopics[grpTopicId]={total:0,reviewed:0};
        grpTopics[grpTopicId].total++;
        if (grpIsReviewed) grpTopics[grpTopicId].reviewed++;
      }
    }
    return json({status:"success",result:"success",subjects:grpSubjects,topics:grpTopics});
  }

  // ── addExamAppearance — একটা প্রশ্নের নতুন appearance (post+institution+year)
  // যোগ করে — মূল প্রশ্নের রো একটুও touch হয় না, শুধু এই ছোট ট্যাবে ১টা নতুন রো ──
  if (action==="addExamAppearance") {
    var aeaQid=(e.parameter.questionId||"").toString().trim();
    var aeaPostId=(e.parameter.postId||"").toString().trim();
    var aeaInstId=(e.parameter.institutionId||"").toString().trim();
    var aeaYear=(e.parameter.year||"").toString().trim();
    if (!aeaQid||!aeaPostId||!aeaInstId||!aeaYear) return json({status:"error",result:"error",message:"questionId/postId/institutionId/year সবগুলো প্রয়োজন"});
    var aeaSs=SpreadsheetApp.getActiveSpreadsheet(), aeaSh=aeaSs.getSheetByName("Exam_Appearances");
    if (!aeaSh) return json({status:"error",result:"error",message:"Exam_Appearances sheet নেই"});
    var aeaNewId="EA-"+Utilities.getUuid().substring(0,8);
    aeaSh.appendRow([aeaNewId, aeaQid, aeaPostId, aeaInstId, aeaYear]);
    return json({status:"success",result:"success",appearanceId:aeaNewId});
  }

  // ── deleteExamAppearance — একটা নির্দিষ্ট appearance-এন্ট্রি মুছে দেয় (appearance_id
  // দিয়ে), মূল প্রশ্ন বা বাকি appearance-গুলো touch হয় না। ভুল করে যোগ হওয়া
  // পদ/প্রতিষ্ঠান/সাল সরানোর জন্য (Browse-এর 🧾 কুইক-মডাল থেকে ব্যবহার হয়)। ──
  if (action==="deleteExamAppearance") {
    var deaId=(e.parameter.appearanceId||"").toString().trim();
    if (!deaId) return json({status:"error",result:"error",message:"appearanceId প্রয়োজন"});
    var deaSs=SpreadsheetApp.getActiveSpreadsheet(), deaSh=deaSs.getSheetByName("Exam_Appearances");
    if (!deaSh || deaSh.getLastRow()<2) return json({status:"error",result:"error",message:"Exam_Appearances sheet খালি"});
    var deaData=deaSh.getDataRange().getValues(), deaHdr=deaData[0];
    var deaIdCol=deaHdr.indexOf("appearance_id");
    for (var de=deaData.length-1; de>=1; de--){
      if ((deaData[de][deaIdCol]||"").toString().trim()===deaId){
        deaSh.deleteRow(de+1);
        return json({status:"success",result:"success",deleted:1});
      }
    }
    return json({status:"error",result:"error",message:"এই appearance_id পাওয়া যায়নি"});
  }

  // ── getAllExamAppearances — পুরো Exam_Appearances ট্যাব একবারে বাল্ক-ফেচ (Android
  // User App-এর "পদ অনুযায়ী ব্রাউজ" ফ্লো-র জন্য — getExamAppearances-এর মতো একটা
  // questionId দিয়ে scope করা না, পুরো টেবিল)। getReferenceData-এর মতোই ছোট টেবিল
  // বলে বাল্ক-ফেচ নিরাপদ (Subjects/Topics/Tags/Posts/Institutions-এর সমান আকারের) ──
  if (action==="getAllExamAppearances") {
    var gaeaSs=SpreadsheetApp.getActiveSpreadsheet(), gaeaSh=gaeaSs.getSheetByName("Exam_Appearances");
    if (!gaeaSh || gaeaSh.getLastRow()<2) return json({status:"success",result:"success",appearances:[]});
    var gaeaData=gaeaSh.getDataRange().getValues(), gaeaHdr=gaeaData[0];
    var gaeaIdCol=gaeaHdr.indexOf("appearance_id");
    var gaeaOut=[];
    for (var gae=1;gae<gaeaData.length;gae++){
      if (gaeaIdCol>=0 && !gaeaData[gae][gaeaIdCol]) continue; // খালি রো স্কিপ
      var gaeaRec={};
      for (var gaj=0;gaj<gaeaHdr.length;gaj++) {
        var gaeaKey=gaeaHdr[gaj].toString().trim();
        if (!gaeaKey) continue;
        gaeaRec[gaeaKey]=gaeaData[gae][gaj];
      }
      gaeaOut.push(gaeaRec);
    }
    return json({status:"success",result:"success",appearances:gaeaOut});
  }

  // ── deleteByIds ── ★ delete questions by comma-separated IDs
  if (action==="deleteByIds") {
    return withWriteLock(function(){
    var shName2=e.parameter.sheet||"QBank";
    var shMap3={quiz:"Quiz",qbank:"QBank",study:"Study"};
    shName2=shMap3[shName2.toLowerCase()]||shName2;
    var ids=(e.parameter.ids||"").split(",").map(function(x){return x.trim();}).filter(Boolean);
    if(!ids.length)return json({result:"error",error:"no ids"});
    var ss4=SpreadsheetApp.getActiveSpreadsheet(), sh4=ss4.getSheetByName(shName2);
    if(!sh4)return json({result:"error",error:"sheet not found: "+shName2});
    var d4=sh4.getDataRange().getValues(), h4=d4[0];
    var idIdx=-1;
    for(var ii=0;ii<h4.length;ii++){var hh=h4[ii].toString().toLowerCase().trim();if(hh==="id"||hh==="sl"){idIdx=ii;break;}}
    var dbiTopicIdC=h4.indexOf("topic_id");   // ── dirty-tracking-এর জন্য ──
    var dbiDirty={};
    var deleted=0;
    // Delete from bottom to top to preserve row indices
    for(var i4=d4.length-1;i4>=1;i4--){
      var rowId=idIdx>=0?d4[i4][idIdx].toString():"";
      if(ids.indexOf(rowId)>=0){
        if(dbiTopicIdC>=0) dbiDirty[(d4[i4][dbiTopicIdC]||"").toString()]=1;
        sh4.deleteRow(i4+1);deleted++;
      }
    }
    // ── প্রশ্ন ডিলিট হলে সংশ্লিষ্ট Exam_Appearances রো-ও ক্লিন-আপ করা হয়,
    // নাহলে orphan appearance রো থেকে যেত (এমন question_id-কে পয়েন্ট করে
    // যেটা আর সেভ নেই) ──
    var eaCleanupSh=ss4.getSheetByName("Exam_Appearances");
    var eaDeleted=0;
    if (eaCleanupSh && eaCleanupSh.getLastRow()>=2) {
      var eaData=eaCleanupSh.getDataRange().getValues(), eaHdr=eaData[0];
      var eaQCol=eaHdr.indexOf("question_id");
      if (eaQCol>=0) {
        for (var ei=eaData.length-1;ei>=1;ei--){
          var eaQid=(eaData[ei][eaQCol]||"").toString();
          if (ids.indexOf(eaQid)>=0) { eaCleanupSh.deleteRow(ei+1); eaDeleted++; }
        }
      }
    }
    markTopicsDirty(dbiDirty);
    // Firebase already updated directly from app - DO NOT sync (would overwrite with array)

    // ── AUTO-REINDEX hook — দেখো moveQuestions-এর একই কমেন্ট। deleteByIds
    // এক-এক করে রো মোছে বলে row_start/row_count পুরোপুরি বাসি হয়ে যায়, তাই এখানেও দরকার। ──
    markReindexNeeded_();

    return json({result:"success",deleted:deleted,sheet:shName2,examAppearancesDeleted:eaDeleted});
    });
  }

  // ── deleteByReferenceId — একটা পুরো subject_id/topic_id-এর সব প্রশ্ন
  // একসাথে ডিলিট করে। rebuildIndex-এ বানানো row_start/row_count ব্যবহার করে একটা
  // মাত্র contiguous range delete করে (deleteByIds-এর মতো এক-এক করে হাজার হাজার রো
  // ডিলিট করলে বড় Subject-এ (৭০০০+ প্রশ্ন) ৬-মিনিট execution limit-এ ধাক্কা লাগতে
  // পারত — এটা তার থেকে অনেক দ্রুত)। ডিলিটের পর Topics ইনডেক্স নিজে থেকেই আপডেট
  // (shift/remove) করে দেওয়া হয়, আলাদা করে rebuildIndex চালাতে হয় না। ──
  if (action==="deleteByReferenceId") {
    return withWriteLock(function(){
    var driiType=(e.parameter.refType||"").toLowerCase(); // "subject" | "topic"
    var driiId=(e.parameter.id||"").toString().trim();
    if (!driiId) return json({status:"error",result:"error",message:"id প্রয়োজন"});

    var driiSs=SpreadsheetApp.getActiveSpreadsheet();
    var driiTopicsSh=driiSs.getSheetByName("Topics");
    if (!driiTopicsSh) return json({status:"error",result:"error",message:"Topics sheet নেই"});
    var driiTData=driiTopicsSh.getDataRange().getValues(), driiTHdr=driiTData[0];
    var driiTIdCol=driiTHdr.indexOf("topic_id"), driiSubCol=driiTHdr.indexOf("subject_id");

    // sheet নাম বের করা (subject_id/topic_id-এর প্রিফিক্স থেকে) — কলাম রিজলভ
    // করার *আগে* বের করতে হবে, কারণ row_start/row_count এখন sheet-scoped
    var driiSheetName=driiId.indexOf("QZ")===0?"Quiz":driiId.indexOf("QB")===0?"QBank":driiId.indexOf("ST")===0?"Study":"";
    var driiSh=driiSs.getSheetByName(driiSheetName);
    if (!driiSh) return json({status:"error",result:"error",message:"Sheet not found for id: "+driiId});

    // ── FIX (গুরুতর ডেটা-সেফটি বাগ — "ভুল রেঞ্জ ডিলিট হয়ে যেতে পারতো"):
    // আগে এখানে জেনেরিক legacy row_start/row_count কলাম পড়া হতো, যেটাতে
    // rebuildIndex সবসময় সবচেয়ে শেষে প্রসেস হওয়া sheet-এর (Study) row-range
    // বসিয়ে দিতো (দেখো runRebuildIndexCore()-এর কমেন্ট) — মানে কোনো Quiz/QBank
    // topic_id ডিলিট করতে গেলে legacy কলামে থাকা Study sheet-এর row-range
    // ভুলবশত Quiz/QBank sheet-এ apply হয়ে সম্পূর্ণ ভুল/অসম্পর্কিত রো ডিলিট
    // হয়ে যাওয়ার ঝুঁকি ছিল (getQuestionsPage-এ একই বাগের জন্য আগেই sheet-scoped
    // কলাম যোগ হয়েছিল, কিন্তু এই action-এ তখন মিস হয়ে গিয়েছিল)। এখন driiSheetName
    // অনুযায়ী সঠিক row_start_<sheet>/row_count_<sheet> কলাম পড়া হয় (per-sheet
    // কলাম না থাকলে/পুরনো rebuildIndex চললে legacy-তে fallback করে)। ──
    var driiSheetKey=driiSheetName.toLowerCase();
    var driiRsCol=driiTHdr.indexOf("row_start_"+driiSheetKey), driiRcCol=driiTHdr.indexOf("row_count_"+driiSheetKey);
    if (driiRsCol<0||driiRcCol<0) { driiRsCol=driiTHdr.indexOf("row_start"); driiRcCol=driiTHdr.indexOf("row_count"); }
    if (driiRsCol<0||driiRcCol<0) return json({status:"error",result:"error",message:"Index নেই — আগে action=rebuildIndex চালাও"});

    // ── কোন কোন topic-row (Topics ট্যাবে) এই delete-এ প্রভাবিত হবে, আর
    // Quiz/QBank/Study-তে কোন range মুছতে হবে সেটা বের করা ──
    var driiAffectedTopicRows=[]; // Topics ট্যাবের row index (0-based, data array-এ)
    if (driiType==="topic") {
      for (var dt=1;dt<driiTData.length;dt++){ if((driiTData[dt][driiTIdCol]||"").toString()===driiId){ driiAffectedTopicRows.push(dt); break; } }
    } else if (driiType==="subject") {
      for (var ds=1;ds<driiTData.length;ds++){ if((driiTData[ds][driiSubCol]||"").toString()===driiId) driiAffectedTopicRows.push(ds); }
    } else {
      return json({status:"error",result:"error",message:"অজানা refType: "+driiType+" (subject/topic সাপোর্ট করে)"});
    }
    if (!driiAffectedTopicRows.length) return json({status:"success",result:"success",deleted:0,message:"কিছু পাওয়া যায়নি"});

    var driiStarts=driiAffectedTopicRows.map(function(i){return driiTData[i][driiRsCol];}).filter(Boolean);
    var driiEnds=driiAffectedTopicRows.map(function(i){return driiTData[i][driiRsCol]+driiTData[i][driiRcCol]-1;}).filter(function(v){return v;});
    if (!driiStarts.length) return json({status:"success",result:"success",deleted:0,message:"এই এন্ট্রিতে কোনো প্রশ্ন নেই"});
    var driiRangeStart=Math.min.apply(null,driiStarts);
    var driiRangeEnd=Math.max.apply(null,driiEnds);
    var driiRangeCount=driiRangeEnd-driiRangeStart+1;

    // ── ডিলিট করার আগে ওই রেঞ্জের সব question id ধরে রাখা (Exam_Appearances cleanup-এর জন্য) ──
    var driiHdr=driiSh.getRange(1,1,1,driiSh.getLastColumn()).getValues()[0];
    var driiIdCol=driiHdr.indexOf("id");
    var driiIdsInRange=driiSh.getRange(driiRangeStart,driiIdCol+1,driiRangeCount,1).getValues().map(function(r){return (r[0]||"").toString();});

    driiSh.deleteRows(driiRangeStart,driiRangeCount);

    // ── Exam_Appearances cleanup ──
    var driiEaSh=driiSs.getSheetByName("Exam_Appearances");
    var driiEaDeleted=0;
    if (driiEaSh && driiEaSh.getLastRow()>=2) {
      var driiEaData=driiEaSh.getDataRange().getValues(), driiEaHdr=driiEaData[0];
      var driiEaQCol=driiEaHdr.indexOf("question_id");
      if (driiEaQCol>=0) {
        for (var de=driiEaData.length-1;de>=1;de--){
          if (driiIdsInRange.indexOf((driiEaData[de][driiEaQCol]||"").toString())>=0){ driiEaSh.deleteRow(de+1); driiEaDeleted++; }
        }
      }
    }

    // ── Topics ইনডেক্স আপডেট: মুছে-যাওয়া topic-row(গুলো) বাদ, বাকিদের row_start শিফট
    // (এখন driiRsCol/driiRcCol উপরে sheet-scoped resolve হয়েছে বলে এই শিফটও সঠিক sheet-এ হয়) ──
    var driiRemoveTopicIds={}; driiAffectedTopicRows.forEach(function(i){ driiRemoveTopicIds[driiTData[i][driiTIdCol]]=true; });
    for (var dr=driiTData.length-1;dr>=1;dr--){
      var dTid=(driiTData[dr][driiTIdCol]||"").toString();
      if (driiRemoveTopicIds[dTid]) { driiTopicsSh.deleteRow(dr+1); continue; }
      var dStart=driiTData[dr][driiRsCol];
      if (dStart && dStart>driiRangeEnd) driiTopicsSh.getRange(dr+1,driiRsCol+1).setValue(dStart-driiRangeCount);
    }
    // subject-level delete হলে Subjects ট্যাব থেকেও ওই subject-row বাদ
    if (driiType==="subject") {
      var driiSubjSh=driiSs.getSheetByName("Subjects");
      if (driiSubjSh) {
        var driiSjData=driiSubjSh.getDataRange().getValues(), driiSjHdr=driiSjData[0];
        var driiSjIdCol=driiSjHdr.indexOf("subject_id");
        for (var sj=driiSjData.length-1;sj>=1;sj--){
          if ((driiSjData[sj][driiSjIdCol]||"").toString()===driiId) { driiSubjSh.deleteRow(sj+1); break; }
        }
      }
    }

    // ── CDN dirty-tracking — ডিলিট হওয়া প্রতিটা Topic-কে dirty মার্ক করা হচ্ছে;
    // publish script দেখবে সেই topic_id-তে আর কোনো প্রশ্ন নেই আর GitHub থেকে
    // সংশ্লিষ্ট JSON ফাইল মুছে দেবে (doPublish_-এর বিদ্যমান "questions.length===0"
    // লজিক, দেখো Phase 3 কোড) ──
    var driiDirty={};
    driiAffectedTopicRows.forEach(function(i){ driiDirty[(driiTData[i][driiTIdCol]||"").toString()]=1; });
    markTopicsDirty(driiDirty);

    // ── AUTO-REINDEX hook (FIX "রিইনডেক্স ভুলে যাওয়া লাগে না"): manual shift
    // উপরে শুধু এই sheet-এর row_start_<sheet> ঠিক করে — অন্য sheet-এ যদি একই
    // topic_id-এর আলাদা row_count_<sheet> থাকে সেটা আর legacy কলাম, দুটোই পুরো
    // reindex ছাড়া বাসি থেকে যেত। try/catch দিয়ে গার্ড করা — reindex ব্যর্থ হলেও
    // মূল delete response আটকাবে না (পরের periodic auto-trigger-এই ঠিক হয়ে যাবে)। ──
    markReindexNeeded_();

    return json({status:"success",result:"success",deleted:driiRangeCount,examAppearancesDeleted:driiEaDeleted,sheet:driiSheetName});
    });
  }

  // ── moveQuestions — এক বা একাধিক প্রশ্ন (id দিয়ে, comma-separated) অন্য
  // Subject/Topic-এ move করে। শুধু subject/sub_topic/subject_id/topic_id ফিল্ড বদলায়,
  // প্রশ্নের নিজের id অপরিবর্তিত থাকে (তাই Exam_Appearances/bookmark/quiz-history —
  // কিছুই ভাঙে না, ঠিক যেভাবে renameField-ও id ছোঁয় না)। ──
  if (action==="moveQuestions") {
    return withWriteLock(function(){
    var mqShName=e.parameter.sheet||"";
    var mqShMap={quiz:"Quiz",qbank:"QBank",study:"Study"};
    mqShName=mqShMap[mqShName.toLowerCase()]||mqShName;
    var mqIds=(e.parameter.ids||"").split(",").map(function(x){return x.trim();}).filter(Boolean);
    var mqNewSubject=(e.parameter.newSubject||"").toString().trim();
    var mqNewSubjectId=(e.parameter.newSubjectId||"").toString().trim();
    var mqNewSubTopic=(e.parameter.newSubTopic||"").toString().trim();
    var mqNewTopicId=(e.parameter.newTopicId||"").toString().trim();
    if (!mqIds.length) return json({status:"error",result:"error",message:"ids প্রয়োজন"});
    if (!mqNewSubject||!mqNewSubjectId||!mqNewSubTopic||!mqNewTopicId)
      return json({status:"error",result:"error",message:"newSubject/newSubjectId/newSubTopic/newTopicId প্রয়োজন"});

    var mqSs=SpreadsheetApp.getActiveSpreadsheet(), mqSh=mqSs.getSheetByName(mqShName);
    if (!mqSh) return json({status:"error",result:"error",message:"sheet not found: "+mqShName});
    var mqData=mqSh.getDataRange().getValues(), mqHdr=mqData[0];
    var mqIdCol=mqHdr.indexOf("id");
    var mqSubCol=mqHdr.indexOf("subject");
    var mqSubIdCol=mqHdr.indexOf("subject_id");
    var mqSTCol=mqHdr.indexOf("sub_topic");
    // ⚠️ Study ট্যাবের আসল হেডার "sub_topic" না, "topic" — renameField/updateField-এর
    // মতোই fallback, নাহলে Study-তে move সবসময় "column not found" দিত।
    if (mqSTCol<0) mqSTCol=mqHdr.indexOf("topic");
    var mqTopicIdCol=mqHdr.indexOf("topic_id");
    var mqUpdAtCol=mqHdr.indexOf("updatedAt"); if (mqUpdAtCol<0) mqUpdAtCol=mqHdr.indexOf("updatedat");
    if (mqIdCol<0||mqSubCol<0||mqSTCol<0) return json({status:"error",result:"error",message:"id/subject/sub_topic কলাম পাওয়া যায়নি"});

    var mqDirty={};   // ── dirty-tracking: পুরনো + নতুন টপিক দুটোই (কাউন্ট বদলাবে) ──
    var mqNow=Date.now(), mqMoved=0, mqTouchedRows=[];
    for (var mi=1;mi<mqData.length;mi++){
      var mqRowId=(mqData[mi][mqIdCol]||"").toString().trim();
      if (mqIds.indexOf(mqRowId)<0) continue;
      if (mqTopicIdCol>=0) mqDirty[(mqData[mi][mqTopicIdCol]||"").toString()]=1;   // পুরনো টপিক
      mqSh.getRange(mi+1,mqSubCol+1).setValue(mqNewSubject);
      mqSh.getRange(mi+1,mqSTCol+1).setValue(mqNewSubTopic);
      if (mqSubIdCol>=0) mqSh.getRange(mi+1,mqSubIdCol+1).setValue(mqNewSubjectId);
      if (mqTopicIdCol>=0) mqSh.getRange(mi+1,mqTopicIdCol+1).setValue(mqNewTopicId);
      if (mqUpdAtCol>=0) mqSh.getRange(mi+1,mqUpdAtCol+1).setValue(mqNow);
      mqTouchedRows.push(mi+1);
      mqMoved++;
    }
    if (!mqMoved) return json({status:"error",result:"error",message:"কোনো matching প্রশ্ন পাওয়া যায়নি"});
    mqDirty[mqNewTopicId]=1;   // নতুন টপিক

    // updateField/renameField-এর প্যাটার্ন অনুসরণ — শুধু touched row-গুলোর updatedAt
    // বসিয়ে syncToFirebase-কে incremental patch করতে দেওয়া হয়, পুরো sheet re-upload হয় না
    var mqFbSynced=true;
    if (mqUpdAtCol>=0 && mqTouchedRows.length) mqFbSynced=syncToFirebase(mqShName,mqShName);
    markTopicsDirty(mqDirty);

    // ── AUTO-REINDEX hook (FIX "রিইনডেক্স ভুলে যাওয়া লাগে না"): move করলে
    // পুরনো+নতুন দুই টপিকেরই সঠিক প্রশ্ন-সংখ্যা/রেঞ্জ চাই — এখন এখানেই সাথে সাথে
    // পুরো index রিবিল্ড হয়ে যায়, আলাদা করে rebuildIndex চালানো লাগে না। ──
    markReindexNeeded_();

    return json({status:"success",result:"success",moved:mqMoved,sheet:mqShName,firebaseSynced:mqFbSynced});
    });
  }

  // ── moveTopic — একটা গোটা Topic (তার আন্ডারের সব প্রশ্নসহ) অন্য Subject-এ move
  // করে। mergeTopicId দেওয়া থাকলে destination-এ same নামের existing Topic-এর সাথে
  // merge হয় (সব প্রশ্নের topic_id সেই existing topic_id-তে বসে, আর সোর্স Topic-এর
  // reference-রো ডিলিট হয়ে যায়) — নাহলে topic_id অপরিবর্তিত রেখে শুধু Topics
  // ট্যাবে তার subject_id reparent হয়। প্রশ্নের id/topic_id (merge না হলে) কোনোটাই
  // ভাঙে না — Exam_Appearances/bookmark সব ঠিক থাকে। ──
  if (action==="moveTopic") {
    return withWriteLock(function(){
    var mtTopicId=(e.parameter.topicId||"").toString().trim();
    var mtNewSubjectId=(e.parameter.newSubjectId||"").toString().trim();
    var mtNewSubjectName=(e.parameter.newSubjectName||"").toString().trim();
    var mtNewSubTopicName=(e.parameter.newSubTopicName||"").toString().trim();
    var mtMergeTopicId=(e.parameter.mergeTopicId||"").toString().trim();
    if (!mtTopicId||!mtNewSubjectId||!mtNewSubjectName||!mtNewSubTopicName)
      return json({status:"error",result:"error",message:"topicId/newSubjectId/newSubjectName/newSubTopicName প্রয়োজন"});

    var mtSs=SpreadsheetApp.getActiveSpreadsheet();
    var mtTopicsSh=mtSs.getSheetByName("Topics");
    if (!mtTopicsSh) return json({status:"error",result:"error",message:"Topics sheet নেই"});
    var mtTData=mtTopicsSh.getDataRange().getValues(), mtTHdr=mtTData[0];
    var mtTIdCol=mtTHdr.indexOf("topic_id"), mtTSubCol=mtTHdr.indexOf("subject_id");
    if (mtTIdCol<0||mtTSubCol<0) return json({status:"error",result:"error",message:"Topics ট্যাবে topic_id/subject_id কলাম নেই"});

    var mtFoundRow=-1;
    for (var tr=1;tr<mtTData.length;tr++){ if((mtTData[tr][mtTIdCol]||"").toString().trim()===mtTopicId){ mtFoundRow=tr; break; } }
    if (mtFoundRow<0) return json({status:"error",result:"error",message:"topicId পাওয়া যায়নি: "+mtTopicId});

    // sheet নাম বের করা (topic_id-এর প্রিফিক্স থেকে, deleteByReferenceId-এর মতোই)
    var mtSheetName=mtTopicId.indexOf("QZ")===0?"Quiz":mtTopicId.indexOf("QB")===0?"QBank":mtTopicId.indexOf("ST")===0?"Study":"";
    var mtSh=mtSs.getSheetByName(mtSheetName);
    if (!mtSh) return json({status:"error",result:"error",message:"Sheet not found for topicId: "+mtTopicId});

    var mtEffectiveTopicId=mtMergeTopicId?mtMergeTopicId:mtTopicId;

    if (mtMergeTopicId) {
      // merge — সোর্স Topic-এর reference-রো বাদ (destination-এর existing topic_id-ই থাকবে)
      mtTopicsSh.deleteRow(mtFoundRow+1);
    } else {
      // শুধু reparent — topic_id অপরিবর্তিত, শুধু subject_id বদলায়
      mtTopicsSh.getRange(mtFoundRow+1,mtTSubCol+1).setValue(mtNewSubjectId);
    }

    // ── ডেটা-শিটে (Quiz/QBank/Study) এই টপিকের সব প্রশ্নের subject/sub_topic/
    // subject_id/topic_id বাল্ক-আপডেট ──
    var mtData=mtSh.getDataRange().getValues(), mtHdr=mtData[0];
    var mtSubCol=mtHdr.indexOf("subject");
    var mtSubIdCol=mtHdr.indexOf("subject_id");
    var mtSTCol=mtHdr.indexOf("sub_topic"); if (mtSTCol<0) mtSTCol=mtHdr.indexOf("topic");
    var mtTopicIdCol=mtHdr.indexOf("topic_id");
    var mtUpdAtCol=mtHdr.indexOf("updatedAt"); if (mtUpdAtCol<0) mtUpdAtCol=mtHdr.indexOf("updatedat");
    if (mtSubCol<0||mtSTCol<0||mtTopicIdCol<0) return json({status:"error",result:"error",message:"Data sheet-এ subject/sub_topic/topic_id কলাম নেই"});

    var mtNow=Date.now(), mtMoved=0, mtTouchedRows=[];
    for (var mr=1;mr<mtData.length;mr++){
      if ((mtData[mr][mtTopicIdCol]||"").toString().trim()!==mtTopicId) continue;
      mtSh.getRange(mr+1,mtSubCol+1).setValue(mtNewSubjectName);
      mtSh.getRange(mr+1,mtSTCol+1).setValue(mtNewSubTopicName);
      if (mtSubIdCol>=0) mtSh.getRange(mr+1,mtSubIdCol+1).setValue(mtNewSubjectId);
      mtSh.getRange(mr+1,mtTopicIdCol+1).setValue(mtEffectiveTopicId);
      if (mtUpdAtCol>=0) mtSh.getRange(mr+1,mtUpdAtCol+1).setValue(mtNow);
      mtTouchedRows.push(mr+1);
      mtMoved++;
    }

    var mtFbSynced=true;
    if (mtUpdAtCol>=0 && mtTouchedRows.length) mtFbSynced=syncToFirebase(mtSheetName,mtSheetName);
    // ── dirty-tracking: সোর্স টপিক (এখন হয় খালি, নয়তো merge হয়ে বিলুপ্ত) আর
    // destination টপিক (effectiveTopicId) — দুটোই publish-এ প্রতিফলিত হতে হবে ──
    markTopicDirty(mtTopicId);
    markTopicDirty(mtEffectiveTopicId);

    // ── AUTO-REINDEX hook — দেখো moveQuestions-এর একই কমেন্ট ──
    markReindexNeeded_();

    return json({status:"success",result:"success",moved:mtMoved,sheet:mtSheetName,mergedInto:mtMergeTopicId||null,firebaseSynced:mtFbSynced});
    });
  }


  // ── publishNow — Admin App-এর "Publish Now" বাটন থেকে ট্রিগার হয়। Dirty
  // topic-গুলো GitHub-এ commit করে, manifest.json আপডেট করে। synchronous —
  // dirty topic বেশি হলে কয়েক সেকেন্ড-১/২ মিনিট লাগতে পারে (GAS-এর ৬ মিনিট
  // hard limit-এর মধ্যেই থাকা উচিত স্বাভাবিক ব্যবহারে)। ──
  if (action==="publishNow") {
    return json(publishDirtyTopics());
  }

  // ── getDirtyTopicsCount — Publish বাটনের আগে "কতগুলো Topic অপেক্ষায় আছে"
  // দেখানোর জন্য (read-only, lock লাগে না) ──
  if (action==="getDirtyTopicsCount") {
    var gdcSh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("_DirtyTopics");
    var gdcCount = 0;
    if (gdcSh && gdcSh.getLastRow() >= 2) {
      var gdcIds = gdcSh.getRange(2,1,gdcSh.getLastRow()-1,1).getValues();
      var gdcUniq = {};
      gdcIds.forEach(function(r){ var t=(r[0]||"").toString(); if(t) gdcUniq[t]=1; });
      gdcCount = Object.keys(gdcUniq).length;
    }
    return json({status:"success",result:"success",dirtyCount:gdcCount});
  }

  // ── getPublishStats — CDN-এ এই মুহূর্তে বাস্তবে কতগুলো প্রশ্ন/টপিক আছে তা
  // দেখানোর জন্য (read-only) — সর্বশেষ publish-এর manifest.json সরাসরি
  // GitHub থেকে পড়ে গুনে ফেরত দেয়, কোনো নতুন publish ট্রিগার করে না। এটাই
  // "real-time" যতটা সম্ভব হতে পারে (CDN আসলে যা আছে ঠিক তাই দেখাবে, dirty
  // থাকা টপিকগুলো এখনো এই সংখ্যায় যোগ হবে না যতক্ষণ না পরের Publish হয়)। ──
  if (action==="getPublishStats") {
    var gpsProps = PropertiesService.getScriptProperties();
    var gpsOwner = gpsProps.getProperty("GH_OWNER");
    var gpsRepo = gpsProps.getProperty("GH_REPO");
    var gpsBranch = gpsProps.getProperty("GH_BRANCH") || "main";
    var gpsToken = gpsProps.getProperty("GITHUB_WRITE_TOKEN");
    if (!gpsOwner || !gpsRepo || !gpsToken) {
      return json({status:"error",result:"error",message:"GitHub config (GH_OWNER/GH_REPO/GITHUB_WRITE_TOKEN) সেট করা নেই"});
    }
    var gpsManifestGet = ghGetFile_(gpsOwner, gpsRepo, gpsBranch, "manifest.json", gpsToken);
    if (!gpsManifestGet.exists) {
      return json({status:"success",result:"success",totalQuestions:0,topicCount:0,version:0,publishedAt:null,message:"এখনো কখনো Publish হয়নি"});
    }
    try {
      var gpsManifest = JSON.parse(gpsManifestGet.content);
      var gpsTopics = gpsManifest.topics || {};
      var gpsTotalQ = 0, gpsTopicCount = 0;
      // ── প্রতিটা topic_id-এর prefix দিয়েই কোন Sheet-এর (Quiz/QBank/Study)
      // বোঝা যায় — publishDirtyTopics-এ ঠিক এই একই prefix-detection ব্যবহার
      // হয় (QZ→Quiz, QB→QBank, ST→Study), তাই এখানেও সামঞ্জস্যপূর্ণ রাখা হলো। ──
      var gpsBySheet = {
        Quiz:  {questions:0, topics:0},
        QBank: {questions:0, topics:0},
        Study: {questions:0, topics:0},
      };
      for (var gpsT in gpsTopics) {
        if (!gpsTopics.hasOwnProperty(gpsT)) continue;
        var gpsCount = gpsTopics[gpsT].count || 0;
        gpsTotalQ += gpsCount; gpsTopicCount++;
        var gpsSheetName = gpsT.indexOf("QZ")===0 ? "Quiz" : gpsT.indexOf("QB")===0 ? "QBank" : gpsT.indexOf("ST")===0 ? "Study" : null;
        if (gpsSheetName) { gpsBySheet[gpsSheetName].questions += gpsCount; gpsBySheet[gpsSheetName].topics++; }
      }
      return json({status:"success",result:"success",totalQuestions:gpsTotalQ,topicCount:gpsTopicCount,bySheet:gpsBySheet,version:gpsManifest.version||0,publishedAt:gpsManifest.publishedAt||null});
    } catch (gpsErr) {
      return json({status:"error",result:"error",message:"manifest.json parse ব্যর্থ: "+gpsErr});
    }
  }

  // ── listManifestHistory — manifest.json-এর সাম্প্রতিক কয়েকটা commit (কে,
  // কবে, কোন ভার্সন) দেখায় — Rollback করার আগে "কোনটায় ফিরবো" বেছে নেওয়ার
  // জন্য (read-only, কিছু বদলায় না)। ──
  if (action==="listManifestHistory") {
    var lmhProps = PropertiesService.getScriptProperties();
    var lmhOwner = lmhProps.getProperty("GH_OWNER"), lmhRepo = lmhProps.getProperty("GH_REPO");
    var lmhBranch = lmhProps.getProperty("GH_BRANCH") || "main", lmhToken = lmhProps.getProperty("GITHUB_WRITE_TOKEN");
    if (!lmhOwner || !lmhRepo || !lmhToken) {
      return json({status:"error",result:"error",message:"GitHub config সেট করা নেই"});
    }
    var lmhResult = ghListFileCommits_(lmhOwner, lmhRepo, lmhBranch, "manifest.json", lmhToken, 15);
    if (!lmhResult.success) return json({status:"error",result:"error",message:lmhResult.error});
    // ── প্রতিটা commit-এর manifest content থেকে version/publishedAt/topicCount
    // বের করে দেখানো হচ্ছে, যাতে UI-তে শুধু sha না, "v41 · ৩২০ Topic" এর মতো
    // অর্থবহ কিছু দেখানো যায় — কিন্তু ১৫টা কমিটের প্রতিটার জন্য আলাদা GET কল
    // (fetchWithRetry_-সহ) একটু ধীর হতে পারে, তাই শুধু সাম্প্রতিক কয়েকটাতেই
    // (৮টা) সীমাবদ্ধ রাখা হলো, বাকিগুলো শুধু sha/date/message-সহ ফেরত যায় ──
    var lmhEnriched = lmhResult.commits.map(function(c, idx){
      if (idx >= 8) return c;
      try {
        var lmhFile = ghGetFile_(lmhOwner, lmhRepo, c.sha, "manifest.json", lmhToken);
        if (lmhFile.exists) {
          var lmhM = JSON.parse(lmhFile.content);
          c.version = lmhM.version || 0;
          c.topicCount = Object.keys(lmhM.topics||{}).length;
        }
      } catch (lmhErr) { /* এই একটা commit-এর detail না পেলেও বাকিগুলো দেখানো হবে */ }
      return c;
    });
    return json({status:"success",result:"success",commits:lmhEnriched});
  }

  // ── rollbackManifest — manifest.json-কে আগের কোনো commit-এর অবস্থায়
  // ফিরিয়ে দেয় (নতুন একটা commit হিসেবেই, history মুছে যায় না — এটাই GitHub-এ
  // "revert" করার নিরাপদ উপায়)। ⚠️ এটা শুধু manifest.json ফেরায় — পুরনো
  // manifest যেসব topic ফাইলের কথা বলে, সেই topic JSON ফাইলগুলো GitHub-এই
  // থেকে যায় (কখনো ডিলিট হয় না), তাই ফেরানোর পর সেগুলোও ঠিকই সেই মুহূর্তের
  // কনটেন্ট দেখাবে — সম্পূর্ণ নিরাপদ। withWriteLock-এর ভিতরে, audit trail-এর
  // জন্য _SystemLogs-এ এন্ট্রি থাকে (destructive-ঘেঁষা action বলে)। ──
  if (action==="rollbackManifest") {
    return withWriteLock(function(){
      var rmSha = (e.parameter.sha||"").toString().trim();
      if (!rmSha) return json({status:"error",result:"error",message:"sha দেওয়া হয়নি"});
      var rmProps = PropertiesService.getScriptProperties();
      var rmOwner = rmProps.getProperty("GH_OWNER"), rmRepo = rmProps.getProperty("GH_REPO");
      var rmBranch = rmProps.getProperty("GH_BRANCH") || "main", rmToken = rmProps.getProperty("GITHUB_WRITE_TOKEN");
      if (!rmOwner || !rmRepo || !rmToken) {
        return json({status:"error",result:"error",message:"GitHub config সেট করা নেই"});
      }
      var rmOldFile = ghGetFile_(rmOwner, rmRepo, rmSha, "manifest.json", rmToken);
      if (!rmOldFile.exists) return json({status:"error",result:"error",message:"এই commit-এ manifest.json পাওয়া যায়নি"});
      var rmPut = ghPutFile_(rmOwner, rmRepo, rmBranch, "manifest.json", rmOldFile.content, rmToken, "Rollback manifest.json to " + rmSha.substring(0,7));
      if (!rmPut.success) {
        logError_("rollbackManifest", "commit ব্যর্থ: " + rmPut.error);
        notifyAdminPublishFailure_("Manifest rollback ব্যর্থ: " + rmPut.error);
        return json({status:"error",result:"error",message:rmPut.error});
      }
      logError_("rollbackManifest", "manifest.json rolled back to " + rmSha.substring(0,7));
      return json({status:"success",result:"success",message:"manifest.json ফিরিয়ে দেওয়া হয়েছে"});
    });
  }

  // ── markAllTopicsDirty — "ধাপ ৮: পুরোটা স্কেল করা"-এর জন্য। Phase ১ deploy
  // হওয়ার আগে থেকে থাকা পুরনো সব প্রশ্ন কখনো dirty মার্ক হয়নি (dirty-tracking
  // শুধু নতুন write-এই কাজ করে), তাই সেগুলো এখনো GitHub-এ যায়নি। এই action
  // Topics reference-শিটের সব topic_id একসাথে dirty মার্ক করে দেয় — এরপর
  // একাধিকবার publishNow কল করলে (৪০০-এর cap-এর কারণে) ধীরে ধীরে পুরো
  // প্রশ্নব্যাংক CDN-এ প্রথমবার সম্পূর্ণভাবে publish হয়ে যাবে। এক-কালীন কাজ —
  // এরপর থেকে normal write-action-গুলোই dirty-tracking সামলাবে। ──
  if (action==="markAllTopicsDirty") {
    var matTopicsSh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Topics");
    if (!matTopicsSh) return json({status:"error",result:"error",message:"Topics sheet নেই"});
    var matData = matTopicsSh.getDataRange().getValues(), matHdr = matData[0];
    var matIdCol = matHdr.indexOf("topic_id");
    if (matIdCol < 0) return json({status:"error",result:"error",message:"Topics ট্যাবে topic_id কলাম নেই"});
    var matDirty = {};
    for (var mt=1; mt<matData.length; mt++) {
      var mtId = (matData[mt][matIdCol]||"").toString();
      if (mtId) matDirty[mtId] = 1;
    }
    markTopicsDirty(matDirty);
    return json({status:"success",result:"success",markedCount:Object.keys(matDirty).length});
  }

  /* ══════════════════════════════════════════════════════════════════════
     pruneManifest — "Dirty All Topic + Publish Now" শুধু Topics শিটে
     *বর্তমানে থাকা* topic_id-গুলোই clean করতে পারে (doPublish_ সেই
     topic_id-গুলোর জন্যই GitHub-এ scan চালায়)। কিন্তু কোনো topic_id যদি
     কখনো Topics শিট থেকেই মুছে ফেলা হয় (বা পুরনো/rename হওয়া ID, যা এখন আর
     Topics শিটে নেই), সেটা আর কখনোই dirty হয় না — ফলে তার পুরনো
     qbank/quiz/study JSON ফাইল আর manifest.topics-এর entry চিরদিন GitHub-এ
     আটকে/stuck থেকে যায়, publish যতবারই চালানো হোক না কেন। এই action
     সরাসরি manifest.json-এর সব topic key বনাম Topics শিটের বর্তমান
     topic_id সেট মিলিয়ে, যেগুলো Topics শিটে আর নেই সেগুলোর GitHub ফাইল
     ডিলিট করে + manifest থেকে entry সরিয়ে দেয়। এক-কালীন cleanup action —
     ব্যবহারের পর normal dirty-tracking-ই যথেষ্ট।
     ?action=pruneManifest&dryRun=1  → শুধু কী কী মোছা হতো তার লিস্ট দেখাবে,
                                        আসলে কিছুই মুছবে না (আগে এটা দিয়ে
                                        যাচাই করে নেওয়াই নিরাপদ)
     ?action=pruneManifest           → আসলেই মুছে দেবে + manifest commit করবে
     ══════════════════════════════════════════════════════════════════════ */
  if (action==="pruneManifest") {
    var pmProps = PropertiesService.getScriptProperties();
    var pmGhOwner = pmProps.getProperty("GH_OWNER");
    var pmGhRepo = pmProps.getProperty("GH_REPO");
    var pmGhBranch = pmProps.getProperty("GH_BRANCH") || "main";
    var pmGhToken = pmProps.getProperty("GITHUB_WRITE_TOKEN");
    if (!pmGhOwner || !pmGhRepo || !pmGhToken) {
      return json({status:"error",result:"error",message:"GitHub config Script Properties-এ সেট করা নেই"});
    }
    var pmDryRun = (e.parameter.dryRun === "1" || e.parameter.dryRun === "true");

    var pmTopicsSh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Topics");
    if (!pmTopicsSh) return json({status:"error",result:"error",message:"Topics sheet নেই"});
    var pmTData = pmTopicsSh.getDataRange().getValues(), pmTHdr = pmTData[0];
    var pmTIdCol = pmTHdr.indexOf("topic_id");
    if (pmTIdCol < 0) return json({status:"error",result:"error",message:"Topics ট্যাবে topic_id কলাম নেই"});
    var pmValidIds = {};
    for (var pv=1; pv<pmTData.length; pv++) {
      var pvId = (pmTData[pv][pmTIdCol]||"").toString();
      if (pvId) pmValidIds[pvId] = 1;
    }

    var pmManifestGet = ghGetFile_(pmGhOwner, pmGhRepo, pmGhBranch, "manifest.json", pmGhToken);
    if (!pmManifestGet.exists) {
      return json({status:"success",result:"success",message:"manifest.json এখনো নেই, prune করার কিছু নেই",prunedCount:0});
    }
    var pmManifest = JSON.parse(pmManifestGet.content);
    if (!pmManifest.topics) pmManifest.topics = {};

    var pmOrphanIds = [];
    for (var pmtid in pmManifest.topics) {
      if (pmManifest.topics.hasOwnProperty(pmtid) && !pmValidIds[pmtid]) pmOrphanIds.push(pmtid);
    }

    if (!pmOrphanIds.length) {
      return json({status:"success",result:"success",message:"কোনো orphan manifest entry পাওয়া যায়নি — সব ঠিক আছে",prunedCount:0});
    }

    if (pmDryRun) {
      return json({status:"success",result:"success",dryRun:true,
        message:pmOrphanIds.length+"টা orphan topic পাওয়া গেছে (এখনো মোছা হয়নি — dryRun=1 বাদ দিয়ে আবার কল করলে আসলেই মুছবে)",
        orphanCount:pmOrphanIds.length, orphanTopicIds:pmOrphanIds});
    }

    var pmShaMap = ghGetTree_(pmGhOwner, pmGhRepo, pmGhBranch, pmGhToken);
    var pmPruned = [], pmFailed = [];
    pmOrphanIds.forEach(function (tid) {
      try {
        var pmSheetName = tid.indexOf("QZ") === 0 ? "Quiz" : tid.indexOf("QB") === 0 ? "QBank" : tid.indexOf("ST") === 0 ? "Study" : null;
        if (pmSheetName) {
          var pmFilePath = sheetLowerName_(pmSheetName) + "/" + tid + ".json";
          var pmKnownSha = pmShaMap.hasOwnProperty(pmFilePath) ? pmShaMap[pmFilePath] : null;
          ghDeleteFile_(pmGhOwner, pmGhRepo, pmGhBranch, pmFilePath, pmGhToken, pmKnownSha);
        }
        delete pmManifest.topics[tid];
        pmPruned.push(tid);
      } catch (pmErr) {
        pmFailed.push(tid + ": " + pmErr);
        logError_("pruneManifest", tid + ": " + pmErr);
      }
    });

    // ── subjectTotals পুনর্গণনা (orphan বাদ দিয়ে বাকিদের নিয়ে) ──
    var pmTopicsMapForTotals = {};
    for (var pt=1; pt<pmTData.length; pt++) {
      var ptId = (pmTData[pt][pmTIdCol]||"").toString();
      var ptSubjIdCol = pmTHdr.indexOf("subject_id");
      if (ptId) pmTopicsMapForTotals[ptId] = (pmTData[pt][ptSubjIdCol]||"").toString();
    }
    var pmSubjectTotals = {};
    for (var pmtid2 in pmManifest.topics) {
      if (!pmManifest.topics.hasOwnProperty(pmtid2)) continue;
      var pmSubjId = pmTopicsMapForTotals[pmtid2];
      if (!pmSubjId) continue;
      pmSubjectTotals[pmSubjId] = (pmSubjectTotals[pmSubjId] || 0) + (pmManifest.topics[pmtid2].count || 0);
    }
    pmManifest.subjectTotals = pmSubjectTotals;

    pmManifest.version = (pmManifest.version || 0) + 1;
    pmManifest.publishedAt = Date.now();
    var pmManifestPut = ghPutFile_(pmGhOwner, pmGhRepo, pmGhBranch, "manifest.json", JSON.stringify(pmManifest), pmGhToken, "Prune " + pmPruned.length + " orphan topics (v" + pmManifest.version + ")");
    if (!pmManifestPut.success) {
      return json({status:"error",result:"error",message:"orphan ফাইল মোছা হলেও manifest.json commit ব্যর্থ: "+pmManifestPut.error, prunedCount:pmPruned.length, failed:pmFailed});
    }

    return json({status: pmFailed.length ? "partial" : "success", result: pmFailed.length ? "partial" : "success",
      prunedCount: pmPruned.length, prunedTopicIds: pmPruned, failed: pmFailed, manifestVersion: pmManifest.version});
  }

  // ── countOrphanQuestions — Diagnostic (read-only, নিরাপদ)। কোন Sheet-এ
  // কতগুলো প্রশ্নের topic_id ফাঁকা বা Topics reference-শিটে নেই (orphan/
  // unclassified) — এগুলো markAllTopicsDirty-এর dirty-list-এই কখনো ঢোকে না
  // (সেটা Topics শিট থেকে topic_id নেয়), তাই কখনো publish/CDN-এ যায় না।
  // sanity-check-এ "Sheet total ≠ manifest total" দেখা গেলে এটা দিয়েই সঠিক
  // কারণ ও সংখ্যা বোঝা যাবে। ──
  if (action==="countOrphanQuestions") {
    var coqSs = SpreadsheetApp.getActiveSpreadsheet();
    var coqTopicsSh = coqSs.getSheetByName("Topics");
    var coqValidIds = {};
    if (coqTopicsSh) {
      var coqTData = coqTopicsSh.getDataRange().getValues(), coqTHdr = coqTData[0];
      var coqTIdCol = coqTHdr.indexOf("topic_id");
      if (coqTIdCol >= 0) {
        for (var ct=1; ct<coqTData.length; ct++) {
          var ctid = (coqTData[ct][coqTIdCol]||"").toString();
          if (ctid) coqValidIds[ctid] = 1;
        }
      }
    }
    var coqResult = {};
    ["Quiz", "QBank", "Study"].forEach(function (sheetName) {
      var sh = coqSs.getSheetByName(sheetName);
      if (!sh) { coqResult[sheetName] = { total: 0, blank: 0, orphan: 0 }; return; }
      var data = sh.getDataRange().getValues(), hdr = data[0];
      var topicIdCol = hdr.indexOf("topic_id");
      var total = Math.max(0, data.length - 1);
      var blank = 0, orphan = 0;
      if (topicIdCol >= 0) {
        for (var r=1; r<data.length; r++) {
          var tid = (data[r][topicIdCol]||"").toString();
          if (!tid) blank++;
          else if (!coqValidIds[tid]) orphan++;
        }
      } else {
        blank = total; // কলামই নেই মানে সবগুলোই "topic_id নেই" ধরা হচ্ছে
      }
      coqResult[sheetName] = { total: total, blank: blank, orphan: orphan, ok: total - blank - orphan };
    });
    return json({status:"success",result:"success",bySheet:coqResult});
  }

  // ── deleteOrphanQuestions — countOrphanQuestions যা গোনে তার মধ্যে **শুধু
  // "orphan"** ক্যাটাগরি (topic_id দেওয়া আছে কিন্তু সেই topic_id Topics
  // reference-শিটে অস্তিত্বই নেই — পুরনো টপিক মুছে/rename হয়ে যাওয়ায় প্রশ্নটা
  // "এতিম" হয়ে গেছে) — এটাই একমাত্র জিনিস এখানে মোছা হয়। ⚠️ "blank"
  // ক্যাটাগরি (topic_id একদম ফাঁকা) ইচ্ছাকৃতভাবে **কখনো মোছা হয় না** — এগুলো
  // আসল প্রশ্ন, শুধু এখনো Subject/Topic বসানো বাকি (Admin App-এর Review ট্যাবে
  // এগুলোই ঠিক করা হয়) — bulk delete করলে ভালো প্রশ্ন হারিয়ে যাবে। sheet
  // param না দিলে Quiz/QBank/Study তিনটাতেই চালানো হয়। withWriteLock-এর
  // ভিতরে, প্রতিটা সফল ডিলিটের পর _SystemLogs-এ এন্ট্রি থাকে (audit trail,
  // destructive action বলে)। ──
  if (action==="deleteOrphanQuestions") {
    return withWriteLock(function(){
      var doqSs = SpreadsheetApp.getActiveSpreadsheet();
      var doqTopicsSh = doqSs.getSheetByName("Topics");
      var doqValidIds = {};
      if (doqTopicsSh) {
        var doqTData = doqTopicsSh.getDataRange().getValues(), doqTHdr = doqTData[0];
        var doqTIdCol = doqTHdr.indexOf("topic_id");
        if (doqTIdCol >= 0) {
          for (var dt=1; dt<doqTData.length; dt++) {
            var dtid = (doqTData[dt][doqTIdCol]||"").toString();
            if (dtid) doqValidIds[dtid] = 1;
          }
        }
      }
      var doqSheets = e.parameter.sheet ? [e.parameter.sheet] : ["Quiz","QBank","Study"];
      var doqTotalDeleted = 0;
      var doqBySheet = {};
      doqSheets.forEach(function(sheetName){
        var sh = doqSs.getSheetByName(sheetName);
        if (!sh) { doqBySheet[sheetName]=0; return; }
        var data = sh.getDataRange().getValues(), hdr = data[0];
        var topicIdCol = hdr.indexOf("topic_id");
        if (topicIdCol < 0) { doqBySheet[sheetName]=0; return; }
        var deleted = 0;
        // নিচ থেকে উপরে ডিলিট — নাহলে deleteRow-এর পর বাকি রো-গুলোর index শিফট হয়ে যাবে
        for (var r=data.length-1; r>=1; r--) {
          var tid = (data[r][topicIdCol]||"").toString();
          if (tid && !doqValidIds[tid]) {
            sh.deleteRow(r+1);
            deleted++;
          }
        }
        doqBySheet[sheetName]=deleted;
        doqTotalDeleted += deleted;
      });
      if (doqTotalDeleted>0) logError_("deleteOrphanQuestions", "Deleted "+doqTotalDeleted+" orphan rows: "+JSON.stringify(doqBySheet));
      return json({status:"success",result:"success",deletedCount:doqTotalDeleted,bySheet:doqBySheet});
    });
  }

  // ── adminNotify ──
  if (action==="adminNotify") {
    var adminPhone=(cfg.ADMIN_PHONE||"").toString().replace(/^'+/,'').trim();
    if(!adminPhone)return json({result:"error",error:"ADMIN_PHONE not set"});
    var evType=e.parameter.event||"login";
    var uName=(e.parameter.name||"কেউ");
    var uPhone=(e.parameter.phone||"");
    var extra=(e.parameter.extra||"");

    var title, body, navUrl;

    if(evType==="signup"){
      title="🆕 নতুন Signup!";
      body=uName+" ("+uPhone+") নতুন অ্যাকাউন্ট তৈরি করেছে।";
      navUrl="signups";
    } else if(evType==="login"){
      title="👤 User লগইন";
      body=uName+" ("+uPhone+") লগইন করেছে।";
      navUrl="signups";
    } else if(evType==="technique"){
      title="🧠 নতুন টেকনিক জমা!";
      body=uName+" ("+uPhone+") একটি পাবলিক টেকনিক যোগ করেছে।"+(extra?" প্রশ্ন: "+extra:"");
      navUrl="techniques";
    } else if(evType==="report"){
      title="🚨 নতুন রিপোর্ট!";
      body=uName+" ("+uPhone+") একটি প্রশ্ন রিপোর্ট করেছে।"+(extra?" কারণ: "+extra:"");
      navUrl="reports";
    } else {
      title="🔔 Smart Study";
      body=uName+" ("+uPhone+")";
      navUrl="dashboard";
    }

    return json({result:"success",fcm:sendFCMToPhone(adminPhone,title,body,{type:"admin_"+evType,url:navUrl,questionId:e.parameter.questionId||"",tab:e.parameter.tab||""})});
  }

  // ── resolveReport ──
  if (action==="resolveReport") {
    var phone=(e.parameter.phone||"").toString().replace(/^'+/,'').trim();
    var subject=(e.parameter.subject||"প্রশ্নটি");
    var qid=e.parameter.questionId||"", qsheet=(e.parameter.qsheet||"");
    if(!phone)return json({result:"error",error:"phone missing"});
    var safePhone=phone.replace(/[.#$\[\]\s]/g,'_');
    var payload={type:'report_resolved',title:'✅ রিপোর্ট সমাধান হয়েছে!',body:'"'+subject+'" সংশোধন করা হয়েছে।',questionId:qid,qsheet:qsheet,time:new Date().toLocaleString(),read:false};
    UrlFetchApp.fetch(cfg.FIREBASE_URL+"Notifications/"+safePhone+"/notif_"+Date.now()+".json?auth="+cfg.SECRET_KEY,{method:"put",contentType:"application/json",payload:JSON.stringify(payload),muteHttpExceptions:true});
    return json({result:"success",fcm:sendFCMToPhone(phone,"✅ রিপোর্ট সমাধান!",'"'+subject+'" সংশোধন হয়েছে।',{type:"report_resolved",questionId:qid,url:"report"})});
  }

  // ── sendChallengeNotification ──
  if (action === "sendChallengeNotification") {
    var toPhone   = (e.parameter.toPhone || "").toString().replace(/^'+/, "").trim();
    var fromName  = (e.parameter.fromName  || "কেউ");
    var fromPhone = (e.parameter.fromPhone || "").toString().replace(/^'+/, "").trim();
    var subject   = (e.parameter.subject   || "");
    var subTopic  = (e.parameter.subTopic  || "");
    var chalId    = (e.parameter.challengeId || "").toString().trim();
    var qCount    = (e.parameter.questionCount || "10").toString().trim();
    var wagerXp   = (e.parameter.wagerXp || "0").toString().trim();

    if (!toPhone) return json({ result: "error", error: "toPhone missing" });

    var title = "⚔️ চ্যালেঞ্জ পাঠিয়েছে!";
    var body  = fromName + " তোমাকে " + (subject || "Quiz") + " চ্যালেঞ্জ করেছে। " +
                qCount + "টি প্রশ্ন · " + wagerXp + " XP বাজি!";

    var extraData = {
      type:        "challenge_invite",
      challengeId: chalId,
      fromPhone:   fromPhone,
      fromName:    fromName,
      subject:     subject,
      subTopic:    subTopic,
      url:         "challenge"
    };

    // Firebase Notifications-এও লিখে রাখো (in-app bell এর জন্য)
    var safeToPhone = toPhone.replace(/[.#$\[\]\s]/g, "_");
    var notifPayload = {
      type:        "challenge_invite",
      title:       title,
      body:        body,
      challengeId: chalId,
      fromPhone:   fromPhone,
      time:        new Date().toLocaleString(),
      read:        false
    };
    try {
      UrlFetchApp.fetch(
        cfg.FIREBASE_URL + "Notifications/" + safeToPhone + "/notif_" + Date.now() + ".json?auth=" + cfg.SECRET_KEY,
        { method: "put", contentType: "application/json", payload: JSON.stringify(notifPayload), muteHttpExceptions: true }
      );
    } catch(ne) {}

    return json({ result: "success", fcm: sendFCMToPhone(toPhone, title, body, extraData) });
  }

  // ── personalNotify ──
  if (action==="personalNotify") {
    var phone=(e.parameter.phone||"").toString().replace(/^'+/,'').trim();
    var title=(e.parameter.title||"Smart Study");
    var body=(e.parameter.body||"");
    if(!phone)return json({result:"error",error:"phone missing"});
    var extraData={type:"personal_notification"};
    var nu=(e.parameter.url||"");var nq=(e.parameter.questionId||"");var nqs=(e.parameter.qsheet||"");
    if(nu)extraData.url=nu;if(nq)extraData.questionId=nq;if(nqs)extraData.qsheet=nqs;
    return json({result:"success",fcm:sendFCMToPhone(phone,title,body,extraData)});
  }

  // ── broadcastNotification ──
  if (action==="broadcastNotification") {
    var title=(e.parameter.title||'Smart Study');
    var body=(e.parameter.body||'');
    return json({result:"success",fcm:sendFCMToAll(title,body,{type:"broadcast",url:(e.parameter.url||'qbank')})});
  }

  // ── postNotice ──
  if (action==="postNotice") {
    var ss=SpreadsheetApp.getActiveSpreadsheet(), nSh=ss.getSheetByName("Notice");
    if(!nSh){nSh=ss.insertSheet("Notice");nSh.appendRow(["Date","Title","Message","Timestamp"]);}
    var nTitle=(e.parameter.n_title||""), nMsg=(e.parameter.n_msg||"");
    var nTs=(e.parameter.timestamp||new Date().toLocaleString());
    if(!nTitle||!nMsg)return json({result:"error",error:"missing"});
    nSh.appendRow([nTs.split(",")[0]||Utilities.formatDate(new Date(),"GMT+6","dd/MM/yyyy"),nTitle,nMsg,nTs]);
    syncToFirebase("Notice","Notice");
    return json({result:"success"});
  }

  // ── getSubjects ──
  if (action==="getSubjects") {
    var ss=SpreadsheetApp.getActiveSpreadsheet(), allSubjects={};
    ["Quiz","Study","QBank","Notice"].forEach(function(tabName){
      var tabSheet=ss.getSheetByName(tabName);
      if(tabSheet){
        var tabData=tabSheet.getDataRange().getValues();
        if(tabData.length>1){
          var tabHdr=tabData[0].map(function(h){return h.toString().toLowerCase().trim();});
          var subIdx=tabHdr.indexOf("subject");
          if(subIdx!==-1){var subs=tabData.slice(1).map(function(r){return r[subIdx];});allSubjects[tabName]=subs.filter(function(v,i){return v&&subs.indexOf(v)===i;});}
          else allSubjects[tabName]=[];
        }else allSubjects[tabName]=[];
      }
    });
    return ContentService.createTextOutput(JSON.stringify(allSubjects)).setMimeType(ContentService.MimeType.JSON);
  }

  // ── getDashboard ──
  if (action==="getDashboard") {
    var ss=SpreadsheetApp.getActiveSpreadsheet();
    var out={quiz:{},qbank:{},study:{},reports:[],totalToday:0};
    var today=Utilities.formatDate(new Date(),"GMT+6","dd/MM/yyyy");
    var qSh=ss.getSheetByName("Quiz");
    if(qSh&&qSh.getLastRow()>1){
      var qData=qSh.getDataRange().getValues(), qHdr=qData[0].map(function(h){return h.toString().toLowerCase().trim();});
      var qSubI=qHdr.indexOf("subject"), qTypI=qHdr.indexOf("qtype"), qStI=qHdr.indexOf("sub_topic"), qTsI=qHdr.indexOf("timestamp");
      if(qTypI===-1)qTypI=qHdr.indexOf("question type"); if(qStI===-1)qStI=qHdr.indexOf("subtopic");
      for(var i=1;i<qData.length;i++){
        var sub=(qSubI!==-1?qData[i][qSubI]:"").toString().trim()||"Unknown";
        var qtyp=(qTypI!==-1?qData[i][qTypI]:"MCQ").toString().trim()||"MCQ";
        var stRaw=(qStI!==-1?qData[i][qStI]:"").toString().trim()||"General";
        var isWr=qtyp.toLowerCase()==="written";
        if(!out.quiz[sub])out.quiz[sub]={total:0,mcq:0,written:0,topics:{}};
        out.quiz[sub].total++;if(isWr)out.quiz[sub].written++;else out.quiz[sub].mcq++;
        var topic=stRaw||"General";
        if(!out.quiz[sub].topics[topic])out.quiz[sub].topics[topic]={total:0,mcq:0,written:0};
        out.quiz[sub].topics[topic].total++;
        if(isWr)out.quiz[sub].topics[topic].written++;else out.quiz[sub].topics[topic].mcq++;
        if(qTsI!==-1&&qData[i][qTsI].toString().indexOf(today)!==-1)out.totalToday++;
      }
    }
    var bSh=ss.getSheetByName("QBank");
    if(bSh&&bSh.getLastRow()>1){
      var bData=bSh.getDataRange().getValues(), bHdr=bData[0].map(function(h){return h.toString().toLowerCase().trim();});
      var bSubI=bHdr.indexOf("subject"), bTypI=bHdr.indexOf("qtype"), bTopI=bHdr.indexOf("topic"), bStI=bHdr.indexOf("sub_topic"), bTsI=bHdr.indexOf("timestamp");
      if(bTypI===-1)bTypI=bHdr.indexOf("question type"); if(bStI===-1)bStI=bHdr.indexOf("subtopic");
      for(var j=1;j<bData.length;j++){
        var bsub=(bSubI!==-1?bData[j][bSubI]:"").toString().trim()||"Unknown";
        var btyp=(bTypI!==-1?bData[j][bTypI]:"MCQ").toString().trim()||"MCQ";
        var btop=(bTopI!==-1?bData[j][bTopI]:"").toString().trim()||(bStI!==-1?bData[j][bStI]:"").toString().trim()||"General";
        var bIsWr=btyp.toLowerCase()==="written";
        if(!out.qbank[bsub])out.qbank[bsub]={total:0,mcq:0,written:0,topics:{}};
        out.qbank[bsub].total++;if(bIsWr)out.qbank[bsub].written++;else out.qbank[bsub].mcq++;
        if(!out.qbank[bsub].topics[btop])out.qbank[bsub].topics[btop]={total:0,mcq:0,written:0};
        out.qbank[bsub].topics[btop].total++;
        if(bIsWr)out.qbank[bsub].topics[btop].written++;else out.qbank[bsub].topics[btop].mcq++;
        if(bTsI!==-1&&bData[j][bTsI].toString().indexOf(today)!==-1)out.totalToday++;
      }
    }
    var sSh=ss.getSheetByName("Study"); if(sSh&&sSh.getLastRow()>1){var sData=sSh.getDataRange().getValues();for(var k=1;k<sData.length;k++){var ssub=(sData[k][0]||"Unknown").toString().trim();if(!out.study[ssub])out.study[ssub]={total:0};out.study[ssub].total++;}}
    var rSh=ss.getSheetByName("Reports");
    if(rSh&&rSh.getLastRow()>1){
      var rData=rSh.getDataRange().getValues(), rHdr=rData[0].map(function(h){return h.toString().toLowerCase().trim();});
      var rPhI=rHdr.indexOf("phone"),rSubI=rHdr.indexOf("subject"),rQidI=rHdr.indexOf("questionid"),rQI=rHdr.indexOf("question"),rIsI=rHdr.indexOf("issue"),rTsI=rHdr.indexOf("timestamp");
      if(rQidI===-1)rQidI=rHdr.indexOf("question_id");
      var start=Math.max(1,rData.length-30);
      for(var r=rData.length-1;r>=start;r--){
        out.reports.push({row:r+1,phone:rPhI!==-1?rData[r][rPhI].toString():"",subject:rSubI!==-1?rData[r][rSubI].toString():"",questionId:rQidI!==-1?rData[r][rQidI].toString():"",question:rQI!==-1?rData[r][rQI].toString():"",issue:rIsI!==-1?rData[r][rIsI].toString():"",time:rTsI!==-1?rData[r][rTsI].toString():""});
      }
    }
    return ContentService.createTextOutput(JSON.stringify(out)).setMimeType(ContentService.MimeType.JSON);
  }

  // ── getUsers ──
  if (action==="getUsers") {
    var ss=SpreadsheetApp.getActiveSpreadsheet(), uSh=ss.getSheetByName("Users");
    if(!uSh)return json({error:"Users sheet not found"});
    var uData=uSh.getDataRange().getValues(), uHdr=uData[0].map(function(h){return h.toString().trim();});
    var users=[];
    for(var i=1;i<uData.length;i++){var rec={};for(var j=0;j<uHdr.length;j++){var v=uData[i][j];rec[uHdr[j]]=(v instanceof Date)?Utilities.formatDate(v,"GMT+6","dd-MM-yyyy"):v.toString();}users.push(rec);}
    return json({users:users});
  }

  // ── getTechniques ──
  if (action==="getTechniques") {
    var ss=SpreadsheetApp.getActiveSpreadsheet(), tSh=ss.getSheetByName("Techniques");
    if(!tSh||tSh.getLastRow()<2)return json({techniques:[]});
    var tData=tSh.getDataRange().getValues(), tHdr=tData[0].map(function(h){return h.toString().toLowerCase().trim();});
    var idI=tHdr.indexOf("id"),techI=tHdr.indexOf("technique"),tagI=tHdr.indexOf("tags");
    if(techI===-1)return json({techniques:[]});
    var techs=[];
    for(var i=1;i<tData.length;i++){var tv=tData[i][techI]?tData[i][techI].toString().trim():'';if(!tv)continue;techs.push({id:idI!==-1?tData[i][idI].toString():'',technique:tv,tags:tagI!==-1?tData[i][tagI].toString():''});}
    return json({techniques:techs});
  }

  // ── getSheetRows — সরাসরি Google Sheet থেকে ফুল রো পড়া (Firebase বাইপাস করে) ──
  // Firebase read ব্যর্থ হলে (quota শেষ/নেট সমস্যা) frontend-এর loadPath() স্বয়ংক্রিয়ভাবে
  // এটাকে fallback হিসেবে ব্যবহার করে (SHEET_FALLBACK_TABS: Quiz/QBank/Study/Typing)।
  if (action==="getSheetRows") {
    var grTab=(e.parameter.tab||"QBank").toString().trim();
    var grMap={quiz:"Quiz",qbank:"QBank",study:"Study",typing:"Typing",users:"Users",notice:"Notice",reports:"Reports",curriculumstages:"CurriculumStages"};
    grTab=grMap[grTab.toLowerCase()]||grTab;
    var grSs=SpreadsheetApp.getActiveSpreadsheet(), grSh=grSs.getSheetByName(grTab);
    if(!grSh) return json({status:"error",message:"Sheet not found: "+grTab});
    if(grSh.getLastRow()<2) return json({status:"success",tab:grTab,rows:[]});
    var grData=grSh.getDataRange().getValues();
    var grHdr=grData[0];
    var grRows=[];
    for(var gri=1;gri<grData.length;gri++){
      var grRec={};
      for(var grj=0;grj<grHdr.length;grj++){
        var grKey=grHdr[grj].toString().trim();
        if(!grKey)continue;
        var grVal=grData[gri][grj];
        grRec[grKey]=(grVal instanceof Date)?Utilities.formatDate(grVal,"GMT+6","dd-MM-yyyy HH:mm:ss"):grVal;
      }
      grRec._fbKey=grRec.id||("row"+(gri+1));
      grRows.push(grRec);
    }
    return json({status:"success",tab:grTab,rows:grRows});
  }

  // ── FIX ("পদবী/প্রতিষ্ঠান-মোডে প্রশ্ন ০/০" বাগ): getQuestionsByIds — Exam_Appearances
  // থেকে পাওয়া questionId-লিস্টের মধ্যে যেগুলো ফোনের Room-এ এখনো ক্যাশ হয়নি (কারণ
  // ওই টপিক কখনো স্বাভাবিক Subject→Topic পথে ব্রাউজ করে ডাউনলোড হয়নি), সেগুলো
  // সরাসরি id দিয়ে টার্গেটেড আনার জন্য — getSheetRows-এর মতো পুরো ট্যাব (হাজার হাজার
  // রো) না এনে, শুধু চাওয়া কয়েকটা id-ই রিটার্ন করে (দ্রুত, কম ডেটা)। ──
  if (action==="getQuestionsByIds") {
    var gqiSheet=(e.parameter.sheet||"QBank").toString().trim();
    var gqiMap={quiz:"Quiz",qbank:"QBank",study:"Study"};
    gqiSheet=gqiMap[gqiSheet.toLowerCase()]||gqiSheet;
    var gqiIdsRaw=(e.parameter.ids||"").toString().trim();
    if (!gqiIdsRaw) return json({status:"error",result:"error",message:"ids প্রয়োজন"});
    var gqiIdSet={};
    gqiIdsRaw.split(",").forEach(function(id){ id=id.trim(); if(id) gqiIdSet[id]=true; });

    var gqiSs=SpreadsheetApp.getActiveSpreadsheet(), gqiSh=gqiSs.getSheetByName(gqiSheet);
    if (!gqiSh) return json({status:"error",result:"error",message:"Sheet not found: "+gqiSheet});
    if (gqiSh.getLastRow()<2) return json({status:"success",rows:[]});
    var gqiData=gqiSh.getDataRange().getValues();
    var gqiHdr=gqiData[0];
    var gqiIdCol=gqiHdr.indexOf("id");
    // ── FIX ("পদবী/প্রতিষ্ঠান-মোডে প্রশ্ন ০/০" বাগ, আসল কারণ): Exam_Appearances শীটের
    // question_id আসলে "new_id" ফরম্যাট (QB-00002) — plain "id" (2) না। আগে শুধু "id"
    // কলাম ধরে ম্যাচ করা হতো, তাই Exam_Appearances থেকে আসা কোনো id-ই কখনো মেলেনি।
    // এখন "id" আর "new_id" — দুটো কলামের সাথেই ম্যাচ করা হচ্ছে, যেই ফরম্যাটেই id
    // আসুক (plain "id" বা "new_id") ঠিক কাজ করবে। ──
    var gqiNewIdCol=gqiHdr.indexOf("new_id");
    if (gqiIdCol<0 && gqiNewIdCol<0) return json({status:"error",result:"error",message:"'id'/'new_id' কলাম নেই sheet: "+gqiSheet});

    var gqiRows=[];
    for (var gqi=1; gqi<gqiData.length; gqi++){
      var gqiRowId=gqiIdCol>=0?(gqiData[gqi][gqiIdCol]||"").toString().trim():"";
      var gqiRowNewId=gqiNewIdCol>=0?(gqiData[gqi][gqiNewIdCol]||"").toString().trim():"";
      var gqiMatched=(gqiRowId && gqiIdSet[gqiRowId]) || (gqiRowNewId && gqiIdSet[gqiRowNewId]);
      if (!gqiMatched) continue;
      var gqiRec={};
      for (var gqj=0; gqj<gqiHdr.length; gqj++){
        var gqiKey=gqiHdr[gqj].toString().trim();
        if (!gqiKey) continue;
        var gqiVal=gqiData[gqi][gqj];
        gqiRec[gqiKey]=(gqiVal instanceof Date)?Utilities.formatDate(gqiVal,"GMT+6","dd-MM-yyyy HH:mm:ss"):gqiVal;
      }
      gqiRows.push(gqiRec);
    }
    return json({status:"success",result:"success",rows:gqiRows});
  }


  // ══════════════════════════════════════════════════════════════════════
  // ARCHIVE SECTION — নতুন, সম্পূর্ণ additive (কোনো existing action/লজিক এখানে
  // বদলানো হয়নি)। Archive-এর কোনো row কখনো delete/shift হয় না — শুধু
  // review_status/moved_to_id কলামে ট্যাগ বসে — তাই এই ৪টা action "Topics
  // Archive" শিটের row_start_*/row_count_* index-কে কখনো invalidate করে না।
  // archiveMoveToActive শুধু destination (Quiz/QBank Active) সাইডেই
  // markReindexNeeded_() কল করে, Archive সাইডে reindex লাগে না। ──
  // ──────────────────────────────────────────────────────────────────────
  var ARCHIVE_SHEET_MAP_ = {
    archive_quiz : { data:"Quiz-Archive",  active:"Quiz",  rsCol:"row_start_quiz",  rcCol:"row_count_quiz"  },
    archive_qbank: { data:"QBank-Archive", active:"QBank", rsCol:"row_start_qbank", rcCol:"row_count_qbank" }
  };

  // ── rebuildArchiveIndex — Topics Archive-এর row_start_quiz/row_count_quiz/
  // row_start_qbank/row_count_qbank কলাম (re)build করে। প্রথমবার Archive
  // ব্যবহারের আগে অবশ্যই একবার এটা চালাতে হবে (browser-এ এই action কল করুন),
  // নাহলে app-এর টপিক-লিস্টে বেশিরভাগ টপিক 0/অনুপস্থিত দেখাবে। ──
  if (action==="rebuildArchiveIndex") {
    var raiOut=runRebuildArchiveIndexCore();
    return json({status:"success",result:"success",message:"Archive index rebuilt",details:raiOut});
  }

  // ── getArchiveQuestionsPage — একটা Archive topic-এর ভেতর শুধু unreviewed
  // (review_status খালি) রো-গুলো ৫০-৫০ করে পেজ-বাই-পেজ আনে। cursor এখানে
  // "raw row offset" (topic-block-এর শুরু থেকে কতগুলো রো স্ক্যান করা হয়ে গেছে) —
  // রিটার্ন করা item সংখ্যা না। review_status ট্যাগ বসার কারণে মাঝে কিছু রো
  // স্কিপ হলেও পরের কল ঠিক জায়গা থেকেই আবার শুরু করে — পেজিং কখনো ভাঙে না,
  // কারণ কোনো রো এখানে সরে/শিফট হয় না, শুধু ট্যাগ বসে। ──
  if (action==="getArchiveQuestionsPage") {
    var agpKey=(e.parameter.sheet||"archive_quiz").toString().trim().toLowerCase();
    var agpCfg=ARCHIVE_SHEET_MAP_[agpKey];
    if (!agpCfg) return json({status:"error",result:"error",message:"অজানা archive sheet: "+agpKey});
    var agpTopicId=(e.parameter.topicId||"").toString().trim();
    var agpCursor=parseInt(e.parameter.cursor||"0",10)||0;
    var agpLimit=Math.min(parseInt(e.parameter.limit||"50",10)||50, 100);
    if (!agpTopicId) return json({status:"error",result:"error",message:"topicId প্রয়োজন"});

    var agpSs=SpreadsheetApp.getActiveSpreadsheet();
    var agpSh=agpSs.getSheetByName(agpCfg.data);
    if (!agpSh) return json({status:"error",result:"error",message:"Sheet not found: "+agpCfg.data});
    var agpHdr=agpSh.getRange(1,1,1,agpSh.getLastColumn()).getValues()[0];
    var agpHdrNorm=agpHdr.map(function(h){return h.toString().trim().toLowerCase().replace(/[^a-z0-9]/g,"");});
    var agpRevCol=agpHdrNorm.indexOf("reviewstatus");

    function agpBuildRec(rowArr){
      var rec={};
      for (var k=0;k<agpHdr.length;k++){
        var key=agpHdr[k].toString().trim(); if (!key) continue;
        var val=rowArr[k];
        rec[key]=(val instanceof Date)?Utilities.formatDate(val,"GMT+6","dd-MM-yyyy HH:mm:ss"):val;
      }
      return rec;
    }

    // ── index লুকআপ — Topics Archive শিট থেকে (Topics না!) ──
    var agpEntry=null;
    var agpTopicsSh=agpSs.getSheetByName("Topics Archive");
    if (agpTopicsSh) {
      var agpTData=agpTopicsSh.getDataRange().getValues(), agpTHdr=agpTData[0]||[];
      var agpTIdCol=agpTHdr.indexOf("topic_id");
      var agpRsCol=agpTHdr.indexOf(agpCfg.rsCol), agpRcCol=agpTHdr.indexOf(agpCfg.rcCol);
      if (agpTIdCol>=0 && agpRsCol>=0 && agpRcCol>=0) {
        for (var a1=1;a1<agpTData.length;a1++){
          if ((agpTData[a1][agpTIdCol]||"").toString()===agpTopicId){
            var agpS=agpTData[a1][agpRsCol], agpC=agpTData[a1][agpRcCol];
            if (agpS && agpC) agpEntry={start:agpS,count:agpC};
            break;
          }
        }
      }
    }

    var agpRows=[], agpNextCursor=agpCursor, agpHasMore=false, agpBlockTotal=0;

    if (agpEntry) {
      try {
        if (agpEntry.start<1 || agpEntry.start+agpEntry.count-1>agpSh.getLastRow()) throw new Error("stale archive index");
        agpBlockTotal=agpEntry.count;
        // ── পুরো ব্লক একটাই getRange কলে আনা — লুপের ভেতরে বারবার getRange না
        // করে, নাহলে বেশি already-reviewed রো স্কিপ করতে গেলে quota চাপ বাড়ত ──
        var agpBlockVals=agpSh.getRange(agpEntry.start,1,agpEntry.count,agpSh.getLastColumn()).getValues();
        var agpScan=agpCursor;
        while (agpScan<agpBlockTotal && agpRows.length<agpLimit) {
          var agpVals=agpBlockVals[agpScan];
          agpScan++;
          var agpRevVal=agpRevCol>=0?(agpVals[agpRevCol]||"").toString().trim():"";
          if (agpRevVal) continue; // আগেই reviewed — স্কিপ
          agpRows.push(agpBuildRec(agpVals));
        }
        agpNextCursor=agpScan;
        agpHasMore=agpNextCursor<agpBlockTotal;
      } catch (agpErr) {
        Logger.log("getArchiveQuestionsPage fast-path failed: "+agpErr);
        agpEntry=null; // ── fallback-এ নামো ──
        agpRows=[]; agpNextCursor=agpCursor; agpHasMore=false; agpBlockTotal=0;
      }
    }
    if (!agpEntry) {
      // ── FALLBACK: live scan (index নেই/স্টেল) ──
      var agpTopicColIdx=agpHdrNorm.indexOf("topicid");
      if (agpTopicColIdx<0) return json({status:"error",result:"error",message:"'topic_id' কলাম নেই sheet: "+agpCfg.data});
      var agpAllData=agpSh.getDataRange().getValues();
      var agpMatches=[];
      for (var am=1; am<agpAllData.length; am++){
        if ((agpAllData[am][agpTopicColIdx]||"").toString().trim()===agpTopicId) agpMatches.push(agpAllData[am]);
      }
      agpBlockTotal=agpMatches.length;
      var agpScan2=agpCursor;
      while (agpScan2<agpBlockTotal && agpRows.length<agpLimit) {
        var agpRowArr=agpMatches[agpScan2];
        agpScan2++;
        var agpRevVal2=agpRevCol>=0?(agpRowArr[agpRevCol]||"").toString().trim():"";
        if (agpRevVal2) continue;
        agpRows.push(agpBuildRec(agpRowArr));
      }
      agpNextCursor=agpScan2;
      agpHasMore=agpNextCursor<agpBlockTotal;
    }

    return json({status:"success",result:"success",rows:agpRows,hasMore:agpHasMore,nextCursor:agpNextCursor,total:agpBlockTotal});
  }

  // ── getArchiveQuestionsSorted — A-Z Sort বাটন। একটা টপিকের সব unreviewed
  // রো (পুরো রো — question, option1-4, correct, explanation, id, সব ফিল্ড
  // একসাথে) এনে question-টেক্সট অনুযায়ী সাজিয়ে serial (_srl) বসিয়ে দেয় —
  // কাছাকাছি টেক্সটের ডুপ্লিকেট পাশাপাশি দেখা যায়। এটা display-only —
  // Sheet-এর কোনো রো এখানে নড়ে না, sort হয় শুধু মেমোরিতে। ──
  if (action==="getArchiveQuestionsSorted") {
    var asKey=(e.parameter.sheet||"archive_quiz").toString().trim().toLowerCase();
    var asCfg=ARCHIVE_SHEET_MAP_[asKey];
    if (!asCfg) return json({status:"error",result:"error",message:"অজানা archive sheet: "+asKey});
    var asTopicId=(e.parameter.topicId||"").toString().trim();
    if (!asTopicId) return json({status:"error",result:"error",message:"topicId প্রয়োজন"});
    var asCap=2000; // safety cap — একটা টপিকে সাধারণত এত প্রশ্ন থাকে না (গড় ~২৭টা)

    var asSs=SpreadsheetApp.getActiveSpreadsheet();
    var asSh=asSs.getSheetByName(asCfg.data);
    if (!asSh) return json({status:"error",result:"error",message:"Sheet not found: "+asCfg.data});
    var asHdr=asSh.getRange(1,1,1,asSh.getLastColumn()).getValues()[0];
    var asHdrNorm=asHdr.map(function(h){return h.toString().trim().toLowerCase().replace(/[^a-z0-9]/g,"");});
    var asRevCol=asHdrNorm.indexOf("reviewstatus");
    var asQCol=asHdrNorm.indexOf("question");
    if (asQCol<0) return json({status:"error",result:"error",message:"'question' কলাম নেই sheet: "+asCfg.data});

    var asTopicsSh=asSs.getSheetByName("Topics Archive");
    var asBlockVals=null;
    if (asTopicsSh) {
      var asTData=asTopicsSh.getDataRange().getValues(), asTHdr=asTData[0]||[];
      var asTIdCol=asTHdr.indexOf("topic_id");
      var asRsCol=asTHdr.indexOf(asCfg.rsCol), asRcCol=asTHdr.indexOf(asCfg.rcCol);
      if (asTIdCol>=0 && asRsCol>=0 && asRcCol>=0) {
        for (var s1=1;s1<asTData.length;s1++){
          if ((asTData[s1][asTIdCol]||"").toString()===asTopicId){
            var asS=asTData[s1][asRsCol], asC=asTData[s1][asRcCol];
            if (asS && asC && asS>=1 && asS+asC-1<=asSh.getLastRow()) {
              asBlockVals=asSh.getRange(asS,1,Math.min(asC,asCap),asSh.getLastColumn()).getValues();
            }
            break;
          }
        }
      }
    }
    if (!asBlockVals) {
      // ── fallback: live scan ──
      var asTopicColIdx=asHdrNorm.indexOf("topicid");
      if (asTopicColIdx<0) return json({status:"error",result:"error",message:"'topic_id' কলাম নেই sheet: "+asCfg.data});
      var asAllData=asSh.getDataRange().getValues();
      asBlockVals=[];
      for (var sa=1; sa<asAllData.length && asBlockVals.length<asCap; sa++){
        if ((asAllData[sa][asTopicColIdx]||"").toString().trim()===asTopicId) asBlockVals.push(asAllData[sa]);
      }
    }

    var asRecs=[];
    for (var sb=0;sb<asBlockVals.length;sb++){
      var asRowArr=asBlockVals[sb];
      var asRevVal=asRevCol>=0?(asRowArr[asRevCol]||"").toString().trim():"";
      if (asRevVal) continue; // আগেই reviewed — বাদ, ফলে বারবার একই জিনিস আসবে না
      var asRec={};
      for (var sc=0;sc<asHdr.length;sc++){
        var asKeyName=asHdr[sc].toString().trim(); if (!asKeyName) continue;
        var asVal=asRowArr[sc];
        asRec[asKeyName]=(asVal instanceof Date)?Utilities.formatDate(asVal,"GMT+6","dd-MM-yyyy HH:mm:ss"):asVal;
      }
      asRecs.push(asRec);
    }

    // ── পুরো রো (সব ফিল্ড একসাথে, question/answer/id কিছুই আলাদা হয় না) —
    // শুধু question টেক্সট দিয়ে compare করে সাজানো হচ্ছে ──
    var asQKeyName=asHdr[asQCol].toString().trim();
    asRecs.sort(function(r1,r2){
      var t1=(r1[asQKeyName]||"").toString().trim().toLowerCase();
      var t2=(r2[asQKeyName]||"").toString().trim().toLowerCase();
      if (t1<t2) return -1; if (t1>t2) return 1; return 0;
    });
    for (var sd=0; sd<asRecs.length; sd++) asRecs[sd]._srl=sd+1;

    return json({status:"success",result:"success",rows:asRecs,total:asRecs.length});
  }

  // ── archiveMarkDuplicate — সিলেক্ট করা প্রশ্নগুলোতে review_status="duplicate"
  // বসায় (batch, single-column write, সস্তা অপারেশন) — Archive-এর কোনো রো
  // ডিলিট/শিফট হয় না, তাই index অক্ষত থাকে, reindex লাগে না। পরে আপনি নিজে
  // Sheet-এ গিয়ে review_status="duplicate" ফিল্টার করে বাল্কে ম্যানুয়ালি
  // ডিলিট করবেন — সেটায় কোনো GAS execution লাগে না। ──
  if (action==="archiveMarkDuplicate") {
    return withWriteLock(function(){
    var amdKey=(e.parameter.sheet||"archive_quiz").toString().trim().toLowerCase();
    var amdCfg=ARCHIVE_SHEET_MAP_[amdKey];
    if (!amdCfg) return json({status:"error",result:"error",message:"অজানা archive sheet: "+amdKey});
    var amdIds=(e.parameter.ids||"").split(",").map(function(x){return x.trim();}).filter(Boolean);
    if (!amdIds.length) return json({status:"error",result:"error",message:"ids প্রয়োজন"});

    var amdSs=SpreadsheetApp.getActiveSpreadsheet(), amdSh=amdSs.getSheetByName(amdCfg.data);
    if (!amdSh) return json({status:"error",result:"error",message:"Sheet not found: "+amdCfg.data});
    var amdData=amdSh.getDataRange().getValues(), amdHdr=amdData[0];
    var amdHdrNorm=amdHdr.map(function(h){return h.toString().trim().toLowerCase().replace(/[^a-z0-9]/g,"");});
    var amdIdCol=amdHdrNorm.indexOf("id");
    var amdRevCol=amdHdrNorm.indexOf("reviewstatus");
    if (amdIdCol<0) return json({status:"error",result:"error",message:"'id' কলাম নেই sheet: "+amdCfg.data});
    if (amdRevCol<0) return json({status:"error",result:"error",message:"'review_status' কলাম নেই sheet: "+amdCfg.data});

    var amdIdSet={}; amdIds.forEach(function(id){amdIdSet[id]=true;});
    var amdMarked=0;
    for (var am2=1; am2<amdData.length; am2++){
      var amdRowId=(amdData[am2][amdIdCol]||"").toString().trim();
      if (!amdIdSet[amdRowId]) continue;
      amdSh.getRange(am2+1, amdRevCol+1).setValue("duplicate");
      amdMarked++;
    }
    if (!amdMarked) return json({status:"error",result:"error",message:"কোনো matching প্রশ্ন পাওয়া যায়নি"});
    return json({status:"success",result:"success",marked:amdMarked});
    });
  }

  // ── archiveMoveToActive — সিলেক্ট করা (ভালো/duplicate-না-মার্ক-করা) প্রশ্নগুলো
  // Archive থেকে Active Quiz/QBank শিটে কপি করে (নতুন id সহ, পুরনো id কখনো
  // পুনর্ব্যবহার হয় না) + Archive-এ review_status="moved", moved_to_id=<নতুন id>
  // বসায়। Archive-এর রো ডিলিট হয় না (শুধু ট্যাগ) — তাই Archive-সাইড index
  // অক্ষত থাকে, শুধু destination (Active) সাইডেই reindex দরকার। Subject/Topic
  // — existing হলে dropdown থেকে সিলেক্ট হওয়া নাম পাঠালেই তার existing id
  // ব্যবহার হবে, না থাকলে নতুন Subject/Topic (resolveOrCreateSubjectTopicId,
  // Add-Question ফর্মেও যেটা ব্যবহৃত হয়) নিজে থেকেই তৈরি হয়ে যাবে। ──
  if (action==="archiveMoveToActive") {
    return withWriteLock(function(){
    var amaKey=(e.parameter.sheet||"archive_quiz").toString().trim().toLowerCase();
    var amaCfg=ARCHIVE_SHEET_MAP_[amaKey];
    if (!amaCfg) return json({status:"error",result:"error",message:"অজানা archive sheet: "+amaKey});
    var amaIds=(e.parameter.ids||"").split(",").map(function(x){return x.trim();}).filter(Boolean);
    var amaNewSubject=(e.parameter.newSubject||"").toString().trim();
    var amaNewSubTopic=(e.parameter.newSubTopic||"").toString().trim();
    if (!amaIds.length) return json({status:"error",result:"error",message:"ids প্রয়োজন"});
    if (!amaNewSubject||!amaNewSubTopic) return json({status:"error",result:"error",message:"newSubject/newSubTopic প্রয়োজন"});

    var amaSs=SpreadsheetApp.getActiveSpreadsheet();
    var amaSrcSh=amaSs.getSheetByName(amaCfg.data);
    var amaDstSh=amaSs.getSheetByName(amaCfg.active);
    if (!amaSrcSh) return json({status:"error",result:"error",message:"Sheet not found: "+amaCfg.data});
    if (!amaDstSh) return json({status:"error",result:"error",message:"Sheet not found: "+amaCfg.active});

    var amaSrcData=amaSrcSh.getDataRange().getValues(), amaSrcHdr=amaSrcData[0];
    var amaSrcHdrNorm=amaSrcHdr.map(function(h){return h.toString().trim().toLowerCase().replace(/[^a-z0-9]/g,"");});
    var amaSrcIdCol=amaSrcHdrNorm.indexOf("id");
    var amaRevCol=amaSrcHdrNorm.indexOf("reviewstatus");
    var amaMovedToCol=amaSrcHdrNorm.indexOf("movedtoid");
    if (amaSrcIdCol<0) return json({status:"error",result:"error",message:"'id' কলাম নেই sheet: "+amaCfg.data});
    if (amaRevCol<0) return json({status:"error",result:"error",message:"'review_status' কলাম নেই sheet: "+amaCfg.data});

    var amaDstLastCol=amaDstSh.getLastColumn();
    var amaDstHdr=amaDstSh.getRange(1,1,1,amaDstLastCol).getValues()[0];
    var amaDstHdrNormArr=amaDstHdr.map(function(h){return h.toString().trim().toLowerCase().replace(/[^a-z0-9]/g,"");});
    var amaDstSubCol=amaDstHdrNormArr.indexOf("subject");
    var amaDstSubIdCol=amaDstHdrNormArr.indexOf("subjectid");
    var amaDstTopicCol=amaDstHdrNormArr.indexOf("topic");
    var amaDstTopicIdCol=amaDstHdrNormArr.indexOf("topicid");
    var amaDstIdCol=amaDstHdrNormArr.indexOf("id");
    var amaDstUpdAtCol=amaDstHdrNormArr.indexOf("updatedat");

    // ── existing subject/topic হলে তার id ব্যবহার, না থাকলে নতুন তৈরি ──
    var amaBatchCache={};
    var amaResolved=resolveOrCreateSubjectTopicId(amaCfg.active, amaNewSubject, amaNewSubTopic, amaBatchCache);
    if (!amaResolved.subjectId || !amaResolved.topicId) {
      return json({status:"error",result:"error",message:"Subject/Topic resolve ব্যর্থ"});
    }

    var amaIdSet={}; amaIds.forEach(function(id){amaIdSet[id]=true;});
    var amaNewRows=[], amaSrcRowNums=[], amaNewIds=[];
    var amaNow=Date.now();

    for (var ai=1; ai<amaSrcData.length; ai++){
      var amaSrcRowId=(amaSrcData[ai][amaSrcIdCol]||"").toString().trim();
      if (!amaIdSet[amaSrcRowId]) continue;
      var amaSrcRowArr=amaSrcData[ai];
      // ── source-এর রো-কে normalized-key→value ম্যাপে বানিয়ে destination
      // হেডার অনুযায়ী বসানো — দুই শিটের কলাম-অর্ডার/সংখ্যা আলাদা হলেও (যেমন
      // QBank vs QBank-Archive) কোনো সমস্যা হবে না, নাম মিললেই কপি হবে ──
      var amaSrcRec={};
      for (var aj=0;aj<amaSrcHdr.length;aj++){
        var amaK=amaSrcHdrNorm[aj]; if (!amaK) continue;
        amaSrcRec[amaK]=amaSrcRowArr[aj];
      }
      var amaNewId=getNextId(amaCfg.active);
      var amaDstRow=new Array(amaDstLastCol).fill("");
      for (var ak=0;ak<amaDstHdrNormArr.length;ak++){
        var amaDk=amaDstHdrNormArr[ak]; if (!amaDk) continue;
        if (amaSrcRec.hasOwnProperty(amaDk)) amaDstRow[ak]=amaSrcRec[amaDk];
      }
      if (amaDstIdCol>=0)      amaDstRow[amaDstIdCol]=amaNewId;
      if (amaDstSubCol>=0)     amaDstRow[amaDstSubCol]=amaNewSubject;
      if (amaDstSubIdCol>=0)   amaDstRow[amaDstSubIdCol]=amaResolved.subjectId;
      if (amaDstTopicCol>=0)   amaDstRow[amaDstTopicCol]=amaNewSubTopic;
      if (amaDstTopicIdCol>=0) amaDstRow[amaDstTopicIdCol]=amaResolved.topicId;
      if (amaDstUpdAtCol>=0)   amaDstRow[amaDstUpdAtCol]=amaNow;

      amaNewRows.push(amaDstRow);
      amaSrcRowNums.push(ai+1);
      amaNewIds.push(amaNewId);
    }

    if (!amaNewRows.length) return json({status:"error",result:"error",message:"কোনো matching প্রশ্ন পাওয়া যায়নি"});

    // ── destination-এ batch append (একটাই setValues কল, প্রশ্ন-সংখ্যা যতই হোক) ──
    var amaDstStartRow=amaDstSh.getLastRow()+1;
    amaDstSh.getRange(amaDstStartRow,1,amaNewRows.length,amaDstLastCol).setValues(amaNewRows);

    // ── source (Archive)-এ review_status="moved" + moved_to_id ট্যাগ ──
    for (var al=0; al<amaSrcRowNums.length; al++){
      amaSrcSh.getRange(amaSrcRowNums[al], amaRevCol+1).setValue("moved");
      if (amaMovedToCol>=0) amaSrcSh.getRange(amaSrcRowNums[al], amaMovedToCol+1).setValue(amaNewIds[al]);
    }

    // ── শুধু destination (Active) সাইডের reindex দরকার — Archive-এর
    // block/count অপরিবর্তিত থাকায় Archive-সাইডে reindex লাগছে না ──
    var amaDirty={}; amaDirty[amaResolved.topicId]=1;
    markTopicsDirty(amaDirty);
    markReindexNeeded_();

    return json({status:"success",result:"success",moved:amaNewRows.length,newIds:amaNewIds,subjectId:amaResolved.subjectId,topicId:amaResolved.topicId});
    });
  }

  // ── getAI ──
  if (action==="getAI") {
    var promptText=e.parameter.prompt, apiKey=cfg.GEMINI_API_KEY;
    var models=["gemini-2.0-flash-001","gemini-flash-latest","gemini-1.5-flash"], lastResp="";
    for(var m=0;m<models.length;m++){
      var aiUrl="https://generativelanguage.googleapis.com/v1beta/models/"+models[m]+":generateContent?key="+apiKey;
      try{var aiResp=UrlFetchApp.fetch(aiUrl,{method:"post",contentType:"application/json",muteHttpExceptions:true,payload:JSON.stringify({contents:[{parts:[{text:promptText}]}]})});lastResp=aiResp.getContentText();var aiJson=JSON.parse(lastResp);if(aiJson.candidates&&aiJson.candidates[0].content)return ContentService.createTextOutput(lastResp).setMimeType(ContentService.MimeType.JSON);}catch(ae){lastResp=JSON.stringify({error:ae.toString()});}
    }
    return ContentService.createTextOutput(lastResp).setMimeType(ContentService.MimeType.JSON);
  }

  // ── fallback: get by id+tab ──
  var id=e.parameter.id, tabName=e.parameter.tab;
  if(id&&tabName){
    var ss2=SpreadsheetApp.getActiveSpreadsheet(), getSheet=ss2.getSheetByName(tabName);
    if(!getSheet)return ContentService.createTextOutput(JSON.stringify({status:"error",message:"Sheet not found"})).setMimeType(ContentService.MimeType.JSON);
    var gData=getSheet.getDataRange().getValues(), gHdr=gData[0];
    for(var gi=1;gi<gData.length;gi++){if(gData[gi][0].toString()==id.toString()){var obj={};for(var gj=0;gj<gHdr.length;gj++)obj[gHdr[gj]]=gData[gi][gj];return ContentService.createTextOutput(JSON.stringify({status:"success",data:obj})).setMimeType(ContentService.MimeType.JSON);}}
    return ContentService.createTextOutput(JSON.stringify({status:"error",message:"ID not found"})).setMimeType(ContentService.MimeType.JSON);
  }
  return json({error:"Unknown action: "+action});
 } catch(err) {
  // ── আগে doGet-এর কোনো global try/catch ছিল না — একটা unexpected exception
  // (যেমন আগে decodeURIComponent-এর malformed URI error) হলে GAS নিজে থেকে
  // একটা HTML error page ফেরত দিতো, যেটা Android-সাইডে JSON.parse() করার
  // চেষ্টায় crash/silent-failure হতো (দেখো GasContentService.callGetAction)।
  // doPost-এর মতোই এখন সবসময় proper JSON error ফেরত যাবে। ──
  return json({status:"error",result:"error",message:err.toString(),error:err.toString()});
 }
}

/* ══════════════════════════════════════════════════════════
   doPost
══════════════════════════════════════════════════════════ */
function doPost(e) {
  try {
    var ss=SpreadsheetApp.getActiveSpreadsheet(), cfg=getProps();
    var params=(e.postData&&e.postData.contents)?JSON.parse(e.postData.contents):e.parameter;

    // ── SECRET_KEY VALIDATION ──
    var expectedSecret = cfg.SECRET_KEY;
    var receivedSecret = params.secret || e.parameter.secret || "";
    if (expectedSecret && receivedSecret !== expectedSecret) {
      return json({ status: "error", message: "Unauthorized" });
    }

    // ── QBank→Quiz Converter (Admin App AI Job) বাল্ক ইনসার্ট ──
    // এটা শুধু Sheet-এ লেখে, কখনো syncToFirebase() কল করে না — ইচ্ছাকৃতভাবে,
    // কারণ Firebase quota রিসেট না হওয়া পর্যন্ত এই ডেটা শুধু Sheet-এ staging হিসেবে থাকবে।
    if (params.type === "qbank_to_quiz_bulk") {
      return withWriteLock(function(){
      var targetSheetName = params.targetSheet || "Quiz";
      var qcSh = ss.getSheetByName(targetSheetName);
      if (!qcSh) return json({ result: "error", error: "Sheet not found: " + targetSheetName });

      // 🐛 ফিক্স: আগে এখানে rowData একটা ফিক্সড ১৬-এলিমেন্ট positional array ছিল
      // (subject_id/topic_id/group_id ইত্যাদি কলাম যোগ হওয়ার আগের পুরনো কোড) — তাই
      // subject_id/topic_id কখনো লেখাই হতো না (এই দুটো কলাম array-তে ছিলই না), আর
      // Quiz শিটের আসল কলাম-অর্ডার এখন এই assume করা অর্ডারের সাথে না মেলায় বাকি
      // ফিল্ডও ভুল কলামে বসে যাওয়ার ঝুঁকি ছিল। bulk_save_rows যেভাবে header-name
      // মিলিয়ে row বসায়, এখানেও ঠিক সেই একই পদ্ধতি ব্যবহার করা হচ্ছে — কলাম যেই
      // অর্ডারেই থাকুক, নাম মিলিয়ে সঠিক জায়গায় বসবে। ──
      var qcData=qcSh.getDataRange().getValues();
      var qcRawHdr=qcData.length?qcData[0]:[];
      var qcKeyNorm=function(s){return (s||"").toString().toLowerCase().replace(/[^a-z0-9]/g,"");};
      var qcColIdx={};
      for(var qch=0; qch<qcRawHdr.length; qch++){ qcColIdx[qcKeyNorm(qcRawHdr[qch])]=qch; }
      function qcBuildRow(fieldMap){
        var arr=new Array(qcRawHdr.length).fill("");
        for(var fk in fieldMap){ var ci=qcColIdx[qcKeyNorm(fk)]; if(ci!==undefined) arr[ci]=fieldMap[fk]; }
        return arr;
      }

      // ── ID generation — bulk_save_rows-এর সাথে সামঞ্জস্যপূর্ণ (QZ-00001 স্টাইল),
      // sheet স্ক্যান করেই max বের করা হয় (getNextId()-এর আলাদা script-property
      // কাউন্টারের উপর নির্ভর না করে) — নাহলে দুটো ভিন্ন insert-path আলাদা কাউন্টার
      // ব্যবহার করলে একই id দুইবার জেনারেট হয়ে যাওয়ার (collision) ঝুঁকি থাকে। ──
      var qcPrefix=(targetSheetName==="Quiz"?"QZ-":targetSheetName==="QBank"?"QB-":targetSheetName==="Study"?"ST-":"");
      var qcMaxNum=0;
      var qcIdColIdx=qcColIdx["id"];
      if(qcPrefix && qcIdColIdx!==undefined){
        for(var qcr=1;qcr<qcData.length;qcr++){
          var qcv=(qcData[qcr][qcIdColIdx]||"").toString();
          if(qcv.indexOf(qcPrefix)===0){ var qcn=parseInt(qcv.substring(qcPrefix.length),10); if(!isNaN(qcn)&&qcn>qcMaxNum) qcMaxNum=qcn; }
        }
      }
      var qcCurId=qcMaxNum;

      var rows = params.rows || [];
      var added = 0, skipped = 0, errors = [];
      var qcRefCache={}; // এই ব্যাচের জন্য Subjects/Topics-এর in-memory cache (resolveOrCreateSubjectTopicId দেখো)
      var qcNewRows=[];

      rows.forEach(function(r) {
        try {
          if (isDuplicate(qcSh, r.subject || '', r.question || '', r.sub_topic || '')) {
            skipped++;
            return;
          }
          var newId;
          if(qcPrefix){ qcCurId++; newId=qcPrefix+(qcCurId<10000?("0000"+qcCurId).slice(-5):qcCurId); }
          else { newId=getNextId(targetSheetName); }

          // 🐛 ফিক্স: subject/topic-এর নাম দিয়ে id resolve/create করা হচ্ছে —
          // আগে এই ধাপটাই ছিল না, তাই subject_id/topic_id সবসময় ফাঁকা থাকতো।
          var refIds=resolveOrCreateSubjectTopicId(targetSheetName, r.subject||'', r.sub_topic||'', qcRefCache);

          var rowArr=qcBuildRow({
            "id": newId,
            "question": r.question || '',
            "option1": r.opt1 || '', "option2": r.opt2 || '', "option3": r.opt3 || '', "option4": r.opt4 || '',
            "correct": r.correct || '',
            "subject": r.subject || '',
            "topic": r.sub_topic || '',
            "subject_id": refIds.subjectId,
            "topic_id": refIds.topicId,
            "explanation": r.explanation || '',
            "technique": r.technique || '',
            "previousexam": r.prevExam || '',       // QBank-এর মূল exam paper-এর নাম এখানে থাকবে
            "questiontype": r.qType || 'MCQ',
            "timestamp": r.timestamp || new Date().toLocaleString('bn-BD'),
            "audiencetags": r.audienceTags || 'Job',
            "updatedat": Date.now(),
            "notfirebase": "NF",
            "added_by": "Q2Q",
          });
          qcNewRows.push(rowArr);
          added++;
        } catch (rowErr) {
          errors.push({ q: (r.question || '').substring(0, 40), err: rowErr.toString() });
        }
      });

      if(qcNewRows.length){
        qcSh.getRange(qcSh.getLastRow()+1, 1, qcNewRows.length, qcRawHdr.length).setValues(qcNewRows);
      }

      return json({ result: "success", added: added, skipped: skipped, errors: errors });
      });
    }

    if(params.action==="getAI"||e.parameter.action==="getAI"){
      var promptText=params.prompt||e.parameter.prompt||"", apiKey=cfg.GEMINI_API_KEY;
      var models=["gemini-2.0-flash-001","gemini-flash-latest","gemini-1.5-flash"], lastResp="";
      for(var m=0;m<models.length;m++){try{var aiResp=UrlFetchApp.fetch("https://generativelanguage.googleapis.com/v1beta/models/"+models[m]+":generateContent?key="+apiKey,{method:"post",contentType:"application/json",muteHttpExceptions:true,payload:JSON.stringify({contents:[{parts:[{text:promptText}]}]})});lastResp=aiResp.getContentText();var aiJson=JSON.parse(lastResp);if(aiJson.candidates&&aiJson.candidates[0].content)return ContentService.createTextOutput(lastResp).setMimeType(ContentService.MimeType.JSON);}catch(ae){lastResp=JSON.stringify({error:ae.toString()});}}
      return ContentService.createTextOutput(lastResp).setMimeType(ContentService.MimeType.JSON);
    }

    if(params.type==="save_fcm_token"){
      var phone=(params.phone||'').toString().trim(), token=(params.token||'').toString().trim();
      if(!phone||!token)return json({result:"error",error:"missing"});
      var safePhone=phone.replace(/[.#$\[\]\s]/g,'_');
      UrlFetchApp.fetch(cfg.FIREBASE_URL+"FCMTokens/"+safePhone+".json?auth="+cfg.SECRET_KEY,{method:"put",contentType:"application/json",payload:JSON.stringify({token:token,phone:phone,updatedAt:new Date().toLocaleString()}),muteHttpExceptions:true});
      return json({result:"success"});
    }

    if(params.type==="save_technique"){
      var tSh=ss.getSheetByName("Techniques");
      if(!tSh){tSh=ss.insertSheet("Techniques");tSh.appendRow(["id","Technique","Tags","timestamp"]);}
      var editId=(params.editId||'').toString().trim();
      if(editId){var tData=tSh.getDataRange().getValues();for(var ti=1;ti<tData.length;ti++){if(tData[ti][0].toString()===editId){tSh.getRange(ti+1,1,1,4).setValues([[editId,params.technique||'',params.tags||'',params.timestamp||new Date().toLocaleString()]]);return json({result:"success",id:editId});}}}
      var tId="T"+Date.now(); tSh.appendRow([tId,params.technique||'',params.tags||'',params.timestamp||new Date().toLocaleString()]);
      return json({result:"success",id:tId});
    }

    if(params.type==="resolve_report"){
      var phone=params.phone||'', subject=params.subject||'প্রশ্নটি', qid=params.questionId||'';
      if(phone){
        var safePhone=phone.toString().trim().replace(/[.#$\[\]\s]/g,'_');
        var payload={type:'report_resolved',title:'✅ রিপোর্ট সমাধান হয়েছে!',body:'"'+subject+'" সংশোধন করা হয়েছে।',questionId:qid,time:new Date().toLocaleString(),read:false};
        UrlFetchApp.fetch(cfg.FIREBASE_URL+"Notifications/"+safePhone+"/notif_"+Date.now()+".json?auth="+cfg.SECRET_KEY,{method:"put",contentType:"application/json",payload:JSON.stringify(payload),muteHttpExceptions:true});
        return json({result:"success",fcm:sendFCMToPhone(phone,"✅ রিপোর্ট সমাধান!",'"'+subject+'" সংশোধন হয়েছে।',{type:"report_resolved",questionId:qid})});
      }
      return json({result:"error",error:"phone missing"});
    }

    if(params.type==="broadcast_notification"){
      return json({result:"success",fcm:sendFCMToAll(params.title||'Smart Study',params.body||'',{type:"broadcast"})});
    }

    if(params.type==="update_explanation"){
      var sName=params.sheet, shMap2={qbank:"QBank",quiz:"Quiz",study:"Study",typing:"Typing"};
      sName=shMap2[sName.toLowerCase()]||sName;
      var uSheet=ss.getSheetByName(sName); if(!uSheet)return txt("Sheet not found");
      var uRows=uSheet.getDataRange().getValues(), uHdr=uRows[0].map(function(h){return h.toString().toLowerCase().trim();});
      var idC=uHdr.indexOf("id"), fld=params.field.toLowerCase().trim(), fldC=uHdr.indexOf(fld);
      if(fldC===-1){for(var fc=0;fc<uHdr.length;fc++){if(uHdr[fc].includes(fld)){fldC=fc;break;}}}
      if(idC===-1||fldC===-1)return txt("Column not found");
      var ueAtC=uHdr.indexOf("updatedat");
      // ── dirty-tracking-এর জন্য ──
      var ueTopicIdC=uHdr.indexOf("topic_id");
      for(var ur=1;ur<uRows.length;ur++){
        if(uRows[ur][idC].toString().trim()===params.id.toString().trim()){
          uSheet.getRange(ur+1,fldC+1).setValue(params.content);
          if(ueAtC!==-1)uSheet.getRange(ur+1,ueAtC+1).setValue(Date.now());
          if(!params.bulkMode)syncToFirebase(sName,sName);
          // ── FIX (স্থায়ী সমাধানের ৫ম অংশ): এই handler নামে "update_explanation"
          // হলেও আসলে জেনেরিক single-field updater — generate-explanations.mjs
          // ও generate-mcq-options.mjs script দুটো এটা দিয়েই explanation/option1-4
          // (আসল প্রশ্নের কনটেন্ট) সরাসরি Sheet-এ লেখে, বাল্কে, হাজার হাজার রো-তে।
          // আগে এখানে markTopicDirty() কখনোই কল হতো না — তাই এই script দুটো দিয়ে
          // লেখা কনটেন্ট Sheet-এ ঠিকই বসত কিন্তু কখনো CDN-এ publish হতো না, যতক্ষণ
          // না অন্য কোনো path (edit/move) দিয়ে সেই একই টপিক আবার আলাদাভাবে ছোঁয়া
          // হতো। এখন প্রতিটা সফল লেখায় সেই রো-র topic_id dirty মার্ক হয়, আর field
          // নিজেই topic_id হলে (ভবিষ্যতে কেউ এই জেনেরিক endpoint দিয়ে topic_id
          // বদলালে) reindex flag-ও ওঠে — update_fields-এ আগেই যেই একই ফিক্স
          // করা হয়েছিল, ঠিক সেই একই প্যাটার্ন। ──
          var ueDirtyTopicId = (ueTopicIdC>=0 && fldC===ueTopicIdC) ? params.content.toString() : (ueTopicIdC>=0 ? (uRows[ur][ueTopicIdC]||"").toString() : "");
          if (ueDirtyTopicId) markTopicDirty(ueDirtyTopicId);
          if (ueTopicIdC>=0 && fldC===ueTopicIdC) markReindexNeeded_();
          return txt("Successfully Updated");
        }
      }
      return txt("ID not found: "+params.id);
    }

    if(params.type==="update_xp"){
      var xpSh=ss.getSheetByName("Users"); if(!xpSh)return txt("Users sheet not found");
      var xpRows=xpSh.getDataRange().getValues(), xpHdr=xpRows[0].map(function(h){return h.toString().toLowerCase().trim();});
      var xpPh=xpHdr.indexOf("phone"), xpCol=xpHdr.indexOf("xp");
      if(xpCol===-1){xpCol=xpRows[0].length;xpSh.getRange(1,xpCol+1).setValue("XP");}
      for(var xr=1;xr<xpRows.length;xr++){if(xpPh!==-1&&xpRows[xr][xpPh].toString().trim()===params.phone.toString().trim()){xpSh.getRange(xr+1,xpCol+1).setValue(params.xp);syncToFirebase("Users","Users");return txt("XP Updated");}}
      return txt("User not found");
    }

    if(params.type==="update_picture"){
      var pSh=ss.getSheetByName("Users"); if(!pSh)return txt("Sheet not found");
      var pRows=pSh.getDataRange().getValues(), pHdr=pRows[0].map(function(h){return h.toString().toLowerCase().trim();});
      var pPhCol=pHdr.indexOf("phone"), pPicCol=pHdr.indexOf("picture");
      if(pPhCol===-1||pPicCol===-1)return txt("Column not found");
      var searchPhone=params.phone.toString().trim().replace(/^'+/,'');
      for(var pr=1;pr<pRows.length;pr++){var rowPhone=pRows[pr][pPhCol].toString().trim().replace(/^'+/,'');if(rowPhone.replace(/^0+/,'')===searchPhone.replace(/^0+/,'')){pSh.getRange(pr+1,pPicCol+1).setValue(params.picture_url);syncToFirebase("Users","Users");return txt("Picture Updated");}}
      return txt("User not found");
    }

    // ── update_fields — একসাথে একাধিক কলাম (Question/Opt1-4/Correct/Explanation/
    //    Technique ইত্যাদি) এক id-এর জন্য এক কলে আপডেট। 🐛 ফিক্স (Admin App-এর
    //    InlineEditModal-এ "Edit ব্যর্থ, ফিল্ড: opt1, opt3" জাতীয় random ফিল্ড
    //    ব্যর্থ হওয়া): আগে ক্লায়েন্ট প্রতিটা ফিল্ডের জন্য আলাদা "updateField" কল
    //    parallel-এ পাঠাতো (৮টা ফিল্ড = ৮টা আলাদা রিকোয়েস্ট) — প্রতিটা কল নিজে থেকেই
    //    withWriteLock() (script-wide lock, ৩০সে wait) নিতো, পুরো শিট আবার
    //    getDataRange().getValues() দিয়ে পড়তো, আর নিজে থেকেই একটা করে syncToFirebase()
    //    (পুরো শিট আবার Firebase-এ পাঠানো) চালাতো। Quiz-এর মতো বড় শিটে (হাজার হাজার
    //    রো) একটা syncToFirebase()-ই কয়েক সেকেন্ড লাগতে পারে — ৮টা parallel কল একই
    //    lock-এর জন্য সিরিয়ালি অপেক্ষা করলে মোট সময় ৩০সে ছাড়িয়ে যেত, তখন যেই
    //    ২-৩টা কল শেষে ছিল সেগুলো lock timeout খেয়ে ব্যর্থ হতো — এলোমেলো, ভিন্ন
    //    ভিন্ন ফিল্ড (মান "a"/"the" ইত্যাদির সাথে এর কোনো সম্পর্ক নেই, নিছক টাইমিং)।
    //    এখন সব ফিল্ড একটা মাত্র POST-এ আসে, একবারই lock নেওয়া হয়, একবারই শিট পড়া
    //    হয়, প্রতিটা ফিল্ড লেখা হয় লুপে, শেষে একবারই syncToFirebase() — তাই লক
    //    কনটেনশনই তৈরি হয় না। দেখো src/core/sheetSave.js-এর updateFieldsInSheet(). ──
    if(params.type==="update_fields"){
      return withWriteLock(function(){
      var ufShName=(params.sheet||"").toString();
      var ufShMap={quiz:"Quiz",qbank:"QBank",study:"Study",users:"Users",typing:"Typing"};
      ufShName=ufShMap[ufShName.toLowerCase()]||ufShName;
      var ufSheet=ss.getSheetByName(ufShName);
      if(!ufSheet)return json({result:"error",error:"Sheet not found: "+ufShName});
      var ufRows=ufSheet.getDataRange().getValues();
      var ufHdrRaw=ufRows[0]||[];
      var ufHdr=ufHdrRaw.map(function(h){return h.toString().toLowerCase().trim();});
      var ufNorm2=function(s){return (s||"").toString().toLowerCase().replace(/[^a-z0-9]/g,"");};
      var ufHdrNorm=ufHdrRaw.map(function(h){return ufNorm2(h);});
      var ufIdC=ufHdr.indexOf("id"); if(ufIdC===-1)ufIdC=ufHdr.indexOf("phone");
      var ufTopicIdC=ufHdr.indexOf("topic_id");
      var ufAtC=ufHdrNorm.indexOf("updatedat");
      var ufEditedByC=ufHdrNorm.indexOf("editedby");
      var ufReviewC=ufHdrNorm.indexOf("review");
      var ufTargetId=(params.id||"").toString().trim();
      var ufFields=params.fields||{};
      var ufEditSource=(params.editSource||"Admin App").toString().trim();
      if(ufIdC===-1)return json({result:"error",error:"Column not found: id"});
      if(!ufTargetId)return json({result:"error",error:"id missing"});
      var ufAltMap={"opt1":["opt1","option1"],"opt2":["opt2","option2"],"opt3":["opt3","option3"],"opt4":["opt4","option4"]};
      var ufResolveCol=function(fld){
        var fldNorm=ufNorm2(fld);
        var c=ufHdrNorm.indexOf(fldNorm);
        if(c===-1&&ufAltMap[fld]){for(var ai=0;ai<ufAltMap[fld].length;ai++){c=ufHdrNorm.indexOf(ufNorm2(ufAltMap[fld][ai]));if(c!==-1)break;}}
        if(c===-1){for(var fc=0;fc<ufHdrNorm.length;fc++){if(ufHdrNorm[fc].indexOf(fldNorm)!==-1){c=fc;break;}}}
        if(c===-1&&(fld==="sub_topic"||fld==="subtopic"))c=ufHdrNorm.indexOf("topic");
        return c;
      };
      for(var ur=1;ur<ufRows.length;ur++){
        if(ufRows[ur][ufIdC].toString().trim()===ufTargetId){
          var ufFailed=[], ufReviewLabels=[], ufNewTopicIdVal=null;
          for(var fld in ufFields){
            if(!ufFields.hasOwnProperty(fld))continue;
            var fldLc=fld.toString().toLowerCase().trim();
            var col=ufResolveCol(fldLc);
            if(col===-1){ ufFailed.push(fld); continue; }
            var content=(ufFields[fld]==null?"":ufFields[fld]);
            ufSheet.getRange(ur+1,col+1).setValue(content);
            if(ufTopicIdC>=0&&col===ufTopicIdC) ufNewTopicIdVal=content.toString();
            var rl=reviewLabelForField(fldLc);
            if(rl&&ufReviewLabels.indexOf(rl)===-1) ufReviewLabels.push(rl);
          }
          if(ufAtC!==-1) ufSheet.getRange(ur+1,ufAtC+1).setValue(Date.now());
          if(ufEditedByC!==-1) ufSheet.getRange(ur+1,ufEditedByC+1).setValue(ufEditSource+" - "+new Date().toLocaleString('bn-BD'));
          if(ufReviewC!==-1&&ufReviewLabels.length){
            var ufPrevReview=(ufRows[ur][ufReviewC]||"").toString().trim();
            var ufNextReview=ufPrevReview;
            ufReviewLabels.forEach(function(l){ if(ufNextReview.indexOf(l)===-1) ufNextReview=ufNextReview?(ufNextReview+", "+l):l; });
            ufSheet.getRange(ur+1,ufReviewC+1).setValue(ufNextReview);
          }
          syncToFirebase(ufShName,ufShName);
          var ufDirtyTopicId=ufNewTopicIdVal!==null?ufNewTopicIdVal:(ufTopicIdC>=0?(ufRows[ur][ufTopicIdC]||"").toString():"");
          if(ufDirtyTopicId) markTopicDirty(ufDirtyTopicId);
          // ── FIX: topic_id বদলালে (reclassify/move) সেই রো আর তার আগের
          // contiguous sorted group-এ থাকে না — row_start/row_count-ভিত্তিক
          // index স্টেল হয়ে যায় যতক্ষণ না পরের রিইনডেক্স চলে। ──
          if(ufNewTopicIdVal!==null) markReindexNeeded_();
          return json({result:"success",failed:ufFailed});
        }
      }
      return json({result:"error",error:"ID not found: "+ufTargetId});
      });
    }

    // ── bulk_save_rows — একসাথে অনেক রো Google Sheet-এ সেভ (Save Location = "Google Sheet"
    //    বেছে নিলে QBank→Quiz কনভার্টার, AI Import/OCR direct-submit, বাল্ক আপলোডার — সবাই এই
    //    endpoint ব্যবহার করে)। প্রতিটা রো আলাদাভাবে duplicate-check হয়, শেষে একবারই Firebase sync হয়। ──
    if(params.type==="bulk_save_rows"){
      var bTab=params.targetTab||params.sheet;
      var bSh=ss.getSheetByName(bTab);
      if(!bSh)return json({result:"error",error:"Sheet not found: "+bTab});
      var bRows=params.rows||[];
      if(!bRows.length) return json({result:"success",added:0,skipped:0});

      // ⚡ ফিক্স (আগের পারফরম্যান্স-ফিক্স অক্ষত): শীট একবারই পড়া হয়, ডুপ্লিকেট চেক
      //    in-memory Set দিয়ে হয়, সব নতুন রো শেষে একটাই setValues() কলে ব্যাচ-লেখা হয়।
      var bData=bSh.getDataRange().getValues();
      var bRawHdr=bData.length?bData[0]:[]; // আসল header, order অক্ষত রাখা হলো row-array বানানোর জন্য
      var bHdr=bRawHdr.map(function(h){return h.toString().toLowerCase().trim();});
      var bQIdx=bHdr.indexOf("question"), bSubIdx=bHdr.indexOf("subject"), bStIdx=bHdr.indexOf("sub_topic");
      if(bStIdx===-1)bStIdx=bHdr.indexOf("subtopic");
      var bIdIdx=bHdr.indexOf("id");
      var bNorm=function(s){return (s||'').toString().toLowerCase().replace(/\s+/g,' ').trim().substring(0,100);};
      // ── FIX (ডুপ্লিকেট প্রশ্নে Appearance যোগ): bExisting আগে শুধু true রাখতো (key
      // মিললেই স্কিপ) — এখন সাথে বিদ্যমান রো-র "id"-ও রাখা হয়, যাতে duplicate ধরা
      // পড়লে (নিচে দেখো) সেই id-তে নতুন Exam_Appearance জোড়া যায়, স্রেফ স্কিপ না করে। ──
      var bExisting={};
      if(bQIdx!==-1){
        for(var ber=1;ber<bData.length;ber++){
          var bek=bNorm(bData[ber][bQIdx])+"|"+(bStIdx!==-1?bNorm(bData[ber][bStIdx]):"")+"|"+(bSubIdx!==-1?bNorm(bData[ber][bSubIdx]):"");
          bExisting[bek]=(bIdIdx!==-1?(bData[ber][bIdIdx]||"").toString():true);
        }
      }

      // ── FIX (গুরুত্বপূর্ণ): আগে এখানে row-array পজিশন হার্ডকোড করা হতো (পুরনো
      // কলাম-অর্ডার ধরে নিয়ে) — Phase 2 migration-এ id কলাম সরে যাওয়ায় ও নতুন
      // কলাম (subject_id/topic_id/group_id/sub_index/AudienceTags_ids) যোগ হওয়ায়
      // এই হার্ডকোডেড পজিশন এখন আর সঠিক না — ভুল কলামে ডেটা লেখা হয়ে যেত (silent
      // data corruption)। এখন header-name দিয়ে match করে row array বানানো হয়,
      // sheet-এ কলাম যেই অর্ডারেই থাকুক না কেন সঠিক জায়গায় বসবে। ──
      var bKeyNorm=function(s){return (s||"").toString().toLowerCase().replace(/[^a-z0-9]/g,"");};
      var bColIndexByNormName={};
      for(var bh=0; bh<bRawHdr.length; bh++){ bColIndexByNormName[bKeyNorm(bRawHdr[bh])]=bh; }
      function buildRowArray(fieldMap){
        var arr=new Array(bRawHdr.length).fill("");
        for(var fkey in fieldMap){
          var ci=bColIndexByNormName[bKeyNorm(fkey)];
          if(ci!==undefined) arr[ci]=fieldMap[fkey];
        }
        return arr;
      }

      // ── AUTO-COLUMN-CREATION (FIX: "group_heading/format_style Sheet-এ কলাম না
      // থাকলে সাইলেন্টলি হারিয়ে যাওয়া"): bFieldMap-এ ব্যবহৃত সব কলাম-নাম নিচে তালিকা
      // করা আছে — যেগুলো Sheet-এর header-এ এখনো নেই, সেগুলো এখানেই স্বয়ংক্রিয়ভাবে
      // নতুন কলাম হিসেবে যোগ হয়ে যায় (header সেলে নাম বসিয়ে, in-memory bRawHdr/
      // bColIndexByNormName চওড়া করে) — এরপর buildRowArray() ওই কলামেও ঠিকমতো
      // লিখতে পারবে। ফলে ভবিষ্যতে নতুন ফিল্ড client থেকে পাঠালে Sheet-এ ম্যানুয়ালি
      // কলাম বসানো লাগবে না, নিজে থেকেই প্রথম ব্যবহারেই তৈরি হয়ে যাবে। ──
      var bExpectedCols=["id","question","option1","option2","option3","option4","correct",
        "subject","sub_topic","topic","explanation","technique","previousexam","questiontype",
        "timestamp","audiencetags","questionpaper","visualurl","updatedat","notfirebase",
        "language","content","subjectid","topicid","groupid","subindex","audiencetagsids",
        "groupheading","formatstyle","added_by"];
      for(var eci=0; eci<bExpectedCols.length; eci++){
        var ecName=bExpectedCols[eci];
        if(bColIndexByNormName[bKeyNorm(ecName)]===undefined){
          var newColIdx=bRawHdr.length;
          bSh.getRange(1,newColIdx+1).setValue(ecName);
          bRawHdr.push(ecName);
          bColIndexByNormName[bKeyNorm(ecName)]=newColIdx;
        }
      }

      var bLock=LockService.getScriptLock(); bLock.waitLock(15000);
      var bAdded=0, bSkipped=0;
      var bDirtyTopics={};   // ── CDN dirty-tracking: নতুন প্রশ্নে topic_id দেওয়া
                              // থাকলে (BulkUploaderPage/MultiSubjectImportPage
                              // থেকে) সরাসরি এখানেই মার্ক হবে; না থাকলে (বেশিরভাগ
                              // OCR flow-তে ফাঁকা থাকে) পরে ReviewTab দিয়ে
                              // classify করার সময় updateField-এর মাধ্যমে dirty
                              // মার্ক হবে (নিচে সেই ফিক্সও করা হচ্ছে) ──
      // ── QBank + পদ/প্রতিষ্ঠান/সালের অন্তত ১টা দেওয়া থাকলে (BulkUploaderPage থেকে
      // params.examAppearance অবজেক্ট আসে) — প্রতিটা নতুন QBank প্রশ্নের bId অ্যাসাইন
      // হওয়ার সাথে সাথেই Exam_Appearances-এ একটা করে রো জমা করা হয়, লুপ শেষে একবারে
      // ব্যাচ-write (bNewRows-এর মতোই একই lock-এর ভেতরে, race condition এড়াতে)। ──
      var bAppearanceRows=[];
      var bAppearanceProp, bAppearanceCurId=0;
      var bLinkedExistingCount=0; // ডুপ্লিকেট প্রশ্ন হলেও নতুন appearance যোগ হলে এখানে গোনা হয়
      if(params.examAppearance && bTab==="QBank"){
        bAppearanceProp=PropertiesService.getScriptProperties();
        bAppearanceCurId=parseInt(bAppearanceProp.getProperty("MAX_ID_EXAM_APPEARANCES")||"0");
        if(bAppearanceCurId<1){
          var apSh0=ss.getSheetByName("Exam_Appearances");
          if(apSh0 && apSh0.getLastRow()>1){
            var apIdCol0=apSh0.getRange(2,1,apSh0.getLastRow()-1,1).getValues().map(function(r){
              var m=(r[0]||"").toString().match(/(\d+)$/); return m?parseInt(m[1],10):0;
            });
            bAppearanceCurId=Math.max.apply(null,[0].concat(apIdCol0));
          }
        }
      }
      try{
        // ── ID generation — migration-এর সাথে সামঞ্জস্যপূর্ণ prefix স্কিম
        // (QZ-00001 / QB-00001 / ST-00001), পুরনো plain-numeric MAX_ID_* স্কিমের
        // বদলে — যাতে নতুন ও পুরনো প্রশ্নের id একই ফরম্যাটে থাকে। ──
        var bPrefix=(bTab==="Quiz"?"QZ-":bTab==="QBank"?"QB-":bTab==="Study"?"ST-":"");
        var bMaxNum=0;
        if(bPrefix && bData.length>1 && bQIdx!==-1){
          var bIdColIdx=bColIndexByNormName["id"];
          if(bIdColIdx!==undefined){
            for(var bmr=1;bmr<bData.length;bmr++){
              var bmv=(bData[bmr][bIdColIdx]||"").toString();
              if(bmv.indexOf(bPrefix)===0){ var bmn=parseInt(bmv.substring(bPrefix.length),10); if(!isNaN(bmn)&&bmn>bMaxNum) bMaxNum=bmn; }
            }
          }
        }
        var bCurId=bMaxNum;
        // Typing-এর মতো prefix-বিহীন শিটের জন্য পুরনো numeric স্কিম fallback হিসেবে রাখা হলো
        var bProp=PropertiesService.getScriptProperties(), bIdKey="MAX_ID_"+bTab.toUpperCase();
        var bLegacyCurId=parseInt(bProp.getProperty(bIdKey)||"1000");

        var bNewRows=[];
        var bNowMs=Date.now();
        for(var bi=0;bi<bRows.length;bi++){
          var row=bRows[bi]||{};
          try{
            var bKey=bNorm(row.question)+"|"+bNorm(row.sub_topic)+"|"+bNorm(row.subject);
            // ── FIX (আসল সমস্যা): আগে ডুপ্লিকেট পেলে সাথে সাথে skip করে continue হতো —
            // examAppearance দেওয়া থাকলেও সেটা হারিয়ে যেত, কারণ appearance-attach লজিক
            // নিচে (নতুন রো তৈরির পরে) ছিল, যেটা duplicate-এর জন্য কখনো চলতোই না। এখন
            // duplicate পেলে, যদি examAppearance দেওয়া থাকে (QBank-এই শুধু), তাহলে নতুন রো
            // না বানিয়ে সেই বিদ্যমান প্রশ্নের id-তেই একটা নতুন Exam_Appearance জোড়া হয় —
            // এটাই Admin App-এর "একই প্রশ্ন আবার এলে duplicate না বানিয়ে appearance যোগ
            // করো" ফিচারের মূল সার্ভার-সাইড অংশ। ──
            if(row.question && bExisting[bKey]){
              bSkipped++;
              if(params.examAppearance && bTab==="QBank"){
                var bExistingId=bExisting[bKey];
                if(bExistingId && bExistingId!==true){
                  bAppearanceCurId++;
                  bAppearanceRows.push([
                    "EA"+bAppearanceCurId,
                    bExistingId.toString(),
                    params.examAppearance.postId||"",
                    params.examAppearance.institutionId||"",
                    params.examAppearance.year||""
                  ]);
                  bLinkedExistingCount++;
                }
              }
              continue;
            }

            var bId;
            if(row.editId){ bId=row.editId; }
            else if(bPrefix){ bCurId++; bId=bPrefix+(bCurId<10000?("0000"+bCurId).slice(-5):bCurId); }
            else { bLegacyCurId++; bId=bLegacyCurId; }

            var bFieldMap={
              "id":bId, "question":row.question||"",
              "option1":row.opt1||"", "option2":row.opt2||"", "option3":row.opt3||"",
              "option4":row.opt4||"", "correct":row.correct||"",
              "subject":row.subject||"", "sub_topic":row.sub_topic||"", "topic":row.topic||"",
              "explanation":row.explanation||"", "technique":row.technique||"",
              "previousexam":row.prevExam||"", "questiontype":row.qType||"MCQ",
              "timestamp":row.timestamp||new Date().toLocaleString(),
              "audiencetags":row.audienceTags||"", "questionpaper":row.mainQpaper||"",
              "visualurl":row.visualUrl||"", "updatedat":bNowMs, "notfirebase":"NF",
              "language":row.language||"", "content":row.content||"",
              // ── নতুন schema fields — client (BulkUploaderPage) থেকে দিলে বসবে,
              // না দিলে ফাঁকা থাকবে (পরে Admin App-এর Reference ট্যাব দিয়ে ঠিক করা যাবে) ──
              "subjectid":row.subject_id||"", "topicid":row.topic_id||"",
              "groupid":row.group_id||"",
              "subindex":row.sub_index||"", "audiencetagsids":row.audienceTagsIds||"",
              // 🐛 ফিক্স (Single Entry > QBank > Written — "পরীক্ষার খাতা" PaperComposer):
              // buildSheetRow() ক্লায়েন্ট থেকে group_heading ও format_style পাঠাচ্ছিল, কিন্তু
              // এই bFieldMap-এ কোনো key-ই ছিল না — ফলে এই দুইটা মান সবসময় সাইলেন্টলি ফাঁকা
              // যাচ্ছিল (heading/table/highlight/fillblank সবকিছুই ডাটাবেজে হারিয়ে যাচ্ছিল)।
              // এখন mapping যোগ করা হলো, আর ওপরের AUTO-COLUMN-CREATION ব্লক নিশ্চিত করে
              // Sheet-এ এই কলাম দুটো না থাকলেও প্রথম ব্যবহারেই নিজে থেকে তৈরি হয়ে যাবে —
              // তাই আর কখনোই সাইলেন্টলি হারানোর ঝুঁকি নেই। ──
              "groupheading":row.group_heading||"", "formatstyle":row.format_style||"",
              // 🆕 Added by — কোন ফিচার এই প্রশ্ন যোগ করলো (Bulk_Text/Bulk_OCR/Single_OCR/
              // Single_Text ইত্যাদি, ফ্রন্টএন্ড params.source দিয়ে পাঠায়)। row.editId থাকলে
              // (মানে এটা নতুন ইনসার্ট না, বিদ্যমান রো-র উপর edit/resubmit — যেমন ArchivePage)
              // params.source দিয়ে ওভাররাইট না করে, row নিজে যদি added_by পাঠায় সেটাই রাখা
              // হচ্ছে, নাহলে ফাঁকা রেখে দেওয়া হচ্ছে (আগের মান অক্ষত রাখতে buildRowArray-এর
              // সীমাবদ্ধতা — পুরো রো নতুন করে বসে, তাই edit-path-এ ক্লায়েন্টকেই আগের
              // added_by ফেরত পাঠাতে হবে চাইলে)।
              "added_by": row.added_by||(row.editId?"":(params.source||""))
            };
            var bLine=buildRowArray(bFieldMap);
            if(bTab==="Typing"){ /* Typing-এর সরল schema — শুধু id/language/content/updatedAt/NF দরকার, বাকি field map-এ থাকলেও ক্ষতি নেই কারণ কলাম না থাকলে ignore হয় */ }
            if(bFieldMap["topicid"]) bDirtyTopics[bFieldMap["topicid"].toString()]=1;

            if(!row.editId){ /* id বসানো হয়ে গেছে উপরেই */ }
            bNewRows.push(bLine);
            bExisting[bKey]=bId; // একই ব্যাচে দুইবার একই প্রশ্ন থাকলে দ্বিতীয়টাও এখন bId পাবে (আগে শুধু true থাকতো, appearance জোড়া যেত না)
            bAdded++;
            if(params.examAppearance && bTab==="QBank"){
              bAppearanceCurId++;
              bAppearanceRows.push([
                "EA"+bAppearanceCurId, // appearance_id
                bId,                   // question_id — এইমাত্র assign হওয়া id
                params.examAppearance.postId||"",
                params.examAppearance.institutionId||"",
                params.examAppearance.year||""
              ]);
            }
          }catch(rowErr){ bSkipped++; }
        }
        if(bNewRows.length){
          bSh.getRange(bSh.getLastRow()+1,1,bNewRows.length,bRawHdr.length).setValues(bNewRows);
          markTopicsDirty(bDirtyTopics);
          // ── FIX (স্থায়ী সমাধানের ৩য় অংশ — "নতুন bulk-add করা প্রশ্ন/টপিক app-এ
          // দেখা যায় না"): markTopicsDirty() শুধু CDN publish-এর (manifest.json/
          // topic JSON) জন্য মার্ক করে — কিন্তু app-এর Subject/Topic browse-tree
          // নির্ভর করে Topics শিটের row_count_* কলামের ওপর, যেটা শুধু
          // runRebuildIndexCore() রিফ্রেশ করে। এতদিন bulk_save_rows নতুন রো
          // লেখার পরও কখনো markReindexNeeded_() কল করতো না, তাই periodic
          // ট্রিগার/Publish Now ছাড়া নতুন bulk-added টপিক কখনোই app-এর তালিকায়
          // আসতো না। এখন এখানেও ফ্ল্যাগ সেট হয় — তাই ১৫-মিনিটের মধ্যেই (বা
          // পরের Publish Now-এই) স্বয়ংক্রিয়ভাবে ঠিক হয়ে যাবে। ──
          markReindexNeeded_();
        }
        if(bAppearanceRows.length){
          var apSheet=ss.getSheetByName("Exam_Appearances");
          if(apSheet){
            apSheet.getRange(apSheet.getLastRow()+1,1,bAppearanceRows.length,5).setValues(bAppearanceRows);
            bAppearanceProp.setProperty("MAX_ID_EXAM_APPEARANCES",bAppearanceCurId.toString());
          }
        }
        if(!bPrefix) bProp.setProperty(bIdKey,bLegacyCurId.toString());
      } finally { bLock.releaseLock(); }

      var bShouldSync = (params.sync!==undefined) ? !!params.sync : true; // পুরনো কলার (sync ফ্ল্যাগ ছাড়া) থাকলে আগের মতোই প্রতিবার সিঙ্ক হবে, নতুন ফ্রন্টএন্ড শুধু শেষ চাংকেই sync:true পাঠায়
      var bSyncOk = true;
      if(bShouldSync) bSyncOk = syncToFirebase(bTab,bTab);
      return json({result:"success",added:bAdded,skipped:bSkipped,firebaseSynced:bSyncOk,examAppearancesAdded:bAppearanceRows.length,examAppearancesLinkedToExisting:bLinkedExistingCount});
    }

    // ── নতুন User signup ──
    if(params.targetTab==="Users"){
      var usSh=ss.getSheetByName("Users"); if(!usSh)return json({result:"error",error:"Users sheet not found"});
      var usData=usSh.getDataRange().getValues(), usHdr=usData[0].map(function(h){return h.toString().toLowerCase().trim();});
      var usPh=usHdr.indexOf("phone");
      if(usPh!==-1){for(var ud=1;ud<usData.length;ud++){if(usData[ud][usPh].toString().trim()===(params.phone||'').toString().trim())return json({result:"duplicate",error:"Phone exists"});}}
      usSh.appendRow([params.name||'',params.phone||'',params.email||'',hashPassword(params.password||''),params.type||'Student',params.status||'Active','User','',params.timestamp||new Date().toLocaleString(),params.picture||'',params.userType||'',params.classLevel||'']);
      syncToFirebase("Users","Users");
      return json({result:"success"});
    }

    var tTab=params.targetTab||params.sheet;
    if(params.type==="report")tTab="Reports";
    if(params.type==="bulkSyncDone"){
      var syncTabs=(params.tabs||"").split(",").map(function(t){return t.trim();}).filter(Boolean);
      syncTabs.forEach(function(t){try{syncToFirebase(t,t);}catch(_){}});
      return json({result:"synced",tabs:syncTabs});
    }

    // ⚠️ এক-বারের, ইচ্ছাকৃত অ্যাকশন — আগে থেকে Firebase-এ থাকা ডেটাকে "id" দিয়ে
    // re-key করে (দেখো forceFullRekeySync-এর ওপরের কমেন্ট)। শুধু ম্যানুয়ালি,
    // নিজে থেকে বুঝে-শুনে একবার চালানোর জন্য — কোনো automation এটা নিজে থেকে কল করে না।
    if(params.type==="force_full_rekey_sync"){
      var frSheets=(params.sheets||"Quiz,QBank,Study").split(",").map(function(t){return t.trim();}).filter(Boolean);
      var frResults=frSheets.map(function(s){ return {sheet:s, result:forceFullRekeySync(s,s)}; });
      return json({result:"success",details:frResults});
    }

    // ✅ শুধু "Not Firebase"/"NF" কলামে মার্ক করা row গুলোই sync করে — ছোট,
    // targeted, নিরাপদ (দেখো syncNFRows-এর ওপরের কমেন্ট)।
    if(params.type==="sync_nf_rows"){
      var nfSheets=(params.sheets||"Quiz,QBank,Study").split(",").map(function(t){return t.trim();}).filter(Boolean);
      var nfResults=nfSheets.map(function(s){ return {sheet:s, result:syncNFRows(s,s)}; });
      return json({result:"success",details:nfResults});
    }

    // 🐛 ফিক্স: আগে এখানে txt("Sheet not found: "+tTab) — plain TEXT রেসপন্স — যেটা
    // Android-এর addQuestion() (GasContentService.kt) ভাঙত, কারণ ওটা সবসময় JSON
    // parse করার চেষ্টা করে (Gson MalformedJsonException)। এখন JSON রেসপন্স, তাই
    // অ্যাপে "Sheet not found: ..." মেসেজটা readable ApiResult.Error হিসেবে আসবে,
    // ক্র্যাশ না করে। (এটা "CurriculumStages" ট্যাব তৈরি না করা থাকলে ঠিক এই কারণেই
    // হয় — Sheet-এ ট্যাবটা বানানো লাগবে, headers: id, track, stage, content, updatedAt)
    var mSh=ss.getSheetByName(tTab); if(!mSh)return json({result:"error",error:"Sheet not found: "+tTab});
    if(params.question&&isDuplicate(mSh,params.subject||'',params.question,params.sub_topic||''))
      return json({result:"duplicate",message:"এই sub-topic-এ প্রশ্নটি আগে থেকেই আছে"});

    // ── FIX: আগে এখানে id কলাম হার্ডকোড করে column-0 (প্রথম কলাম) ধরে নেওয়া
    // হতো (mData[ei][0])। Phase 4-এ id কলাম রিপ্লেস/রিপজিশন হওয়ার সম্ভাবনা
    // আছে বলে এখন header name ("id") দিয়ে কলাম খুঁজে নেওয়া হচ্ছে — কলাম
    // যেখানেই থাকুক না কেন এটা সঠিকভাবে কাজ করবে। ──
    var eId=params.editId, rIdx=-1, mData=mSh.getDataRange().getValues(), finalId=eId;
    var mHdr=mData[0]||[];
    var mIdCol=0;
    for (var mh=0; mh<mHdr.length; mh++) { if (mHdr[mh].toString().trim().toLowerCase()==="id") { mIdCol=mh; break; } }
    if(eId){for(var ei=1;ei<mData.length;ei++){if(mData[ei][mIdCol].toString()===eId.toString()){rIdx=ei+1;break;}}}
    if(!eId&&["Quiz","Study","QBank","Typing","CurriculumStages"].indexOf(tTab)>-1)finalId=getNextId(tTab);

    var nowMs=Date.now();

    // ── FIX (bug: নতুন প্রশ্নের subject_id/topic_id ফাঁকা থেকে যাচ্ছিল) ──────────
    // আগে এখানে Quiz/QBank/Study-এর জন্য rData একটা হার্ডকোডেড, ফিক্সড-লেংথ
    // পজিশনাল array ছিল (যেমন Study-তে মাত্র ১১টা ভ্যালু, অথচ Sheet-এ ১৮টা কলাম —
    // subject_id/topic_id/group_id/sub_index/AudienceTags_ids বাদ পড়ে যেত)।
    // rIdx===-1 (নতুন row) হলে mSh.appendRow(rData) সেই বাদ-পড়া কলামগুলো একদম
    // ফাঁকা রেখে দিত — ফলে নতুন যোগ করা প্রতিটা প্রশ্নের topic_id ফাঁকা থাকত, আর
    // যেহেতু Topic লিস্ট/getQuestionsPage সম্পূর্ণ topic_id-নির্ভর (রো row_start/
    // row_count দিয়ে ইনডেক্সড), সেই প্রশ্ন কোনো Topic-এর আন্ডারেই আর দেখাত না —
    // এটাই bulk_save_rows-এ আগে যেই একই বাগ ফিক্স হয়েছিল (দেখো ওপরের কমেন্ট),
    // কিন্তু এই single-row add/edit path-এ তখন মিস হয়ে গিয়েছিল। এখন এখানেও
    // header-name দিয়ে কলাম মেলানো হচ্ছে (mHdr থেকে), যেই কলামই থাকুক আর যেই
    // পজিশনেই থাকুক — সঠিক জায়গায় বসবে, এবং subject_id/topic_id/group_id/
    // sub_index-ও (Android app পাঠালে) ঠিকঠাক লেখা হবে।
    //
    // অ্যালিয়াস: "sub_topic" ↔ "topic" — বাস্তবে Study ট্যাবের আসল কলাম-হেডার
    // "sub_topic" না, "topic" (দেখো Study_Database CSV-র হেডার সারি), অথচ বাকি
    // সব জায়গায় (StudyItem model, isDuplicate, updateField) "sub_topic" নামে
    // কলাম খোঁজা হয়। এখানে দুটো নামই সমান হিসেবে ট্রাই করা হচ্ছে, যেই সাইড থেকেই
    // মিলুক (এটাই আসল/স্থায়ী ফিক্স — Sheet-এর হেডার সেল "topic" থেকে "sub_topic"
    // এ রিনেম করে দিলে এই অ্যালিয়াসটাও আর লাগবে না, কিন্তু ততদিন এটা ছাড়া
    // Study-র sub-topic ফিল্ড write/duplicate-check ভাঙা থাকবে) ──
    var addKeyNorm=function(s){return (s||"").toString().toLowerCase().replace(/[^a-z0-9]/g,"");};
    var ADD_FIELD_ALIASES={
      subtopic: ["sub_topic","subtopic","topic"],
      option1:  ["option1","opt1"], option2: ["option2","opt2"],
      option3:  ["option3","opt3"], option4: ["option4","opt4"]
    };
    function addResolveCol(hdrArr, key){
      var hdrNorm=hdrArr.map(addKeyNorm);
      var nk=addKeyNorm(key);
      var idx=hdrNorm.indexOf(nk);
      if(idx!==-1) return idx;
      var aliases=ADD_FIELD_ALIASES[nk];
      if(aliases){
        for(var ai=0;ai<aliases.length;ai++){
          idx=hdrNorm.indexOf(addKeyNorm(aliases[ai]));
          if(idx!==-1) return idx;
        }
      }
      return -1;
    }
    // fieldMap → header-name-matched row array (rIdx!==-1 হলে বাকি/না-পাঠানো
    // কলামগুলো existing row-এর ভ্যালু ধরে রাখে, যাতে partial edit বাকি কলাম
    // ফাঁকা করে না দেয়; নতুন row হলে না-মেলা কলাম ফাঁকাই থাকবে)
    function addBuildRow(fieldMap){
      var arr=new Array(mHdr.length).fill("");
      if(rIdx!==-1){
        var existing=mData[rIdx-1]||[];
        for(var ci=0;ci<mHdr.length;ci++) arr[ci]=(existing[ci]!==undefined?existing[ci]:"");
      }
      for(var fk in fieldMap){
        var col=addResolveCol(mHdr, fk);
        if(col!==-1) arr[col]=fieldMap[fk];
      }
      return arr;
    } 

    var fieldMap=null;
    if(tTab==="Quiz"){
      fieldMap={
        id: finalId, question: params.question,
        option1: params.opt1, option2: params.opt2, option3: params.opt3, option4: params.opt4,
        correct: params.correct, subject: params.subject, sub_topic: params.sub_topic,
        explanation: params.explanation, technique: params.technique,
        prevExam: params.prevExam||"", qType: params.qType, timestamp: params.timestamp,
        audienceTags: params.audienceTags||"", updatedAt: nowMs,
        subject_id: params.subject_id||"", topic_id: params.topic_id||"",
        group_id: params.group_id||"", sub_index: params.sub_index||""
      };
    } else if(tTab==="QBank"){
      fieldMap={
        id: finalId, question: params.question,
        option1: params.opt1, option2: params.opt2, option3: params.opt3, option4: params.opt4,
        correct: params.correct, subject: params.subject, topic: params.topic,
        sub_topic: params.sub_topic, explanation: params.explanation, technique: params.technique,
        qType: params.qType, mainQpaper: params.mainQpaper||"", timestamp: params.timestamp,
        audienceTags: params.audienceTags||"", updatedAt: nowMs,
        subject_id: params.subject_id||"", topic_id: params.topic_id||"",
        subtopic_id: params.subtopic_id||"", group_id: params.group_id||"", sub_index: params.sub_index||""
      };
    } else if(tTab==="Study"){
      fieldMap={
        id: finalId, subject: params.subject, sub_topic: params.sub_topic,
        question: params.question||"", correct: params.correct||"",
        explanation: params.explanation, technique: params.technique, timestamp: params.timestamp,
        audienceTags: params.audienceTags||"", visualUrl: params.visualUrl||"", updatedAt: nowMs,
        subject_id: params.subject_id||"", topic_id: params.topic_id||"",
        group_id: params.group_id||"", sub_index: params.sub_index||""
      };
    }

    var rData=[];
    if (fieldMap) {
      rData=addBuildRow(fieldMap);
    }
    // ── Typing ট্যাব-এর headers এখন সহজ: id, language, content, updatedAt —
    //    আগে title/level কলামও ছিল, সেগুলো বাদ দেওয়া হলো (Admin App ও এখন এই
    //    ৪টা ফিল্ডই পাঠাবে)। language: "bn" | "en" ──
    else if(tTab==="Typing")rData=[finalId,params.language||"",params.content||"",nowMs];
    // ── CurriculumStages — Smart Typing-এর কারিকুলাম-স্টেজের admin-curated drill
    // কনটেন্ট (Typing-ব্রাঞ্চ merge)। headers: id, track, stage, content, updatedAt।
    // track: "bn"|"en", stage: সংখ্যা। একই track+stage-এ একাধিক row থাকতে পারে
    // (variety), Android অ্যাপ এলোমেলোভাবে একটা বেছে নেয়। কোনো row না থাকলে
    // Android নিজে সিন্থেটিক টেক্সট বানিয়ে নেয় (CurriculumProvider.buildDrillPassage)।
    else if(tTab==="CurriculumStages")rData=[finalId,params.track||"",params.stage||"",params.content||"",nowMs];
    else if(tTab==="Notice")rData=[params.timestamp?params.timestamp.split(',')[0]:"",params.n_title,params.n_msg,params.timestamp];
    else if(tTab==="Reports"){
      var phone=(params.Phone||"").toString().replace(/^'+/,'').trim();
      var phoneForSheet=phone?("'"+phone):"";
      rData=[phoneForSheet,params.QSheet||"",params.Subject||"",params.SubTopic||params.Topic||"",params.QuestionID||"",params.Question||"",params.Issue||"",params.Timestamp||params.timestamp||new Date().toLocaleString('bn-BD')];
    }

    if(rData.length===0)return json({result:"error",error:"Unknown tab"});
    var writtenRow;
    if(rIdx!==-1){ mSh.getRange(rIdx,1,1,rData.length).setValues([rData]); writtenRow=rIdx; }
    else { mSh.appendRow(rData); writtenRow=mSh.getLastRow(); }

    // ── FIX (স্থায়ী সমাধানের ৪র্থ অংশ — একই বাগ এই single-question add/edit
    // পাথেও ছিল, কিন্তু আগে কখনো ধরাই পড়েনি): bulk_save_rows-এর মতো এই
    // পাথও এতদিন markTopicDirty()/markReindexNeeded_() কখনোই কল করতো না —
    // তাই এই পাথ দিয়ে (আলাদা কোনো bulk uploader ছাড়া, সরাসরি একটা প্রশ্ন)
    // যোগ করা প্রতিটা প্রশ্ন CDN-এ কখনো publish হতো না, আর app-এর
    // Subject/Topic তালিকাতেও কখনো আসতো না, যতক্ষণ না কেউ ম্যানুয়ালি অন্য
    // কোনো পথে সেই টপিক আবার ছুঁয়ে দিতো। এখন নতুন রো (rIdx===-1) বা
    // topic_id-সহ যেকোনো edit — দুটোতেই dirty+reindex ফ্ল্যাগ বসে। ──
    if (fieldMap) {
      var addTopicIdVal = (fieldMap.topic_id || "").toString();
      if (addTopicIdVal) markTopicDirty(addTopicIdVal);
      if (rIdx===-1 || addTopicIdVal) markReindexNeeded_();
    }

    // ✅ NF (Not Firebase) স্বয়ংক্রিয় বুককিপিং — sync-এর আগে pessimistically "NF" বসানো
    // হয়, sync সফল হলে মুছে ফেলা হয়। sync ব্যর্থ হলে (যেমন Firebase quota exceeded)
    // NF-ই থেকে যায় — ম্যানুয়ালি মার্ক করার আর দরকার নেই, পরে "sync_nf_rows" অ্যাকশন
    // দিয়ে রিট্রাই করা যাবে।
    var nfIdx=-1;
    if(["Quiz","QBank","Study","Typing","CurriculumStages"].indexOf(tTab)>-1){
      var nfHdrRow=mSh.getRange(1,1,1,mSh.getLastColumn()).getValues()[0].map(function(h){return h.toString().toLowerCase().replace(/\s+/g,"");});
      nfIdx=nfHdrRow.indexOf("notfirebase");
      if(nfIdx===-1) nfIdx=nfHdrRow.indexOf("nf");
    }
    if(nfIdx!==-1) mSh.getRange(writtenRow,nfIdx+1).setValue("NF");
    if(!params.bulkMode){
      var syncOk=syncToFirebase(tTab,tTab);
      if(syncOk===true && nfIdx!==-1) mSh.getRange(writtenRow,nfIdx+1).setValue("");
    }
    return json({result:"success",id:finalId});

  }catch(err){return json({result:"error",error:err.toString()});}
}

/* ══ DashStats Cache ══ */
function updateDashStats() {
  try {
    var cfg=getProps(), ss=SpreadsheetApp.getActiveSpreadsheet();
    var out={quiz:{},qbank:{},study:{},quizTotal:0,qbankTotal:0,studyTotal:0,reportTotal:0,updatedAt:new Date().toISOString()};
    var qSh=ss.getSheetByName("Quiz");
    if(qSh&&qSh.getLastRow()>1){var qData=qSh.getDataRange().getValues(),qHdr=qData[0].map(function(h){return h.toString().toLowerCase().trim();});var qSubI=qHdr.indexOf("subject"),qTypI=qHdr.indexOf("qtype"),qStI=qHdr.indexOf("sub_topic");if(qTypI===-1)qTypI=qHdr.indexOf("question type");if(qStI===-1)qStI=qHdr.indexOf("subtopic");for(var i=1;i<qData.length;i++){var sub=(qSubI!==-1?qData[i][qSubI]:"").toString().trim()||"Unknown";var qtyp=(qTypI!==-1?qData[i][qTypI]:"MCQ").toString().trim();var stRaw=(qStI!==-1?qData[i][qStI]:"").toString().trim()||"General";var isWr=qtyp.toLowerCase()==="written";if(!out.quiz[sub])out.quiz[sub]={total:0,mcq:0,written:0};out.quiz[sub].total++;out.quizTotal++;if(isWr)out.quiz[sub].written++;else out.quiz[sub].mcq++;}}
    var bSh=ss.getSheetByName("QBank");
    if(bSh&&bSh.getLastRow()>1){var bData=bSh.getDataRange().getValues(),bHdr=bData[0].map(function(h){return h.toString().toLowerCase().trim();});var bSubI=bHdr.indexOf("subject"),bTypI=bHdr.indexOf("qtype");if(bTypI===-1)bTypI=bHdr.indexOf("question type");for(var j=1;j<bData.length;j++){var bsub=(bSubI!==-1?bData[j][bSubI]:"").toString().trim()||"Unknown";var btyp=(bTypI!==-1?bData[j][bTypI]:"MCQ").toString().trim();var bIsWr=btyp.toLowerCase()==="written";if(!out.qbank[bsub])out.qbank[bsub]={total:0,mcq:0,written:0};out.qbank[bsub].total++;out.qbankTotal++;if(bIsWr)out.qbank[bsub].written++;else out.qbank[bsub].mcq++;}}
    var sSh=ss.getSheetByName("Study"); if(sSh&&sSh.getLastRow()>1)out.studyTotal=sSh.getLastRow()-1;
    var rSh=ss.getSheetByName("Reports"); if(rSh&&rSh.getLastRow()>1)out.reportTotal=rSh.getLastRow()-1;
    UrlFetchApp.fetch(cfg.FIREBASE_URL+"_DashStats.json?auth="+cfg.SECRET_KEY,{method:"put",contentType:"application/json",payload:JSON.stringify(out)});
    Logger.log("✅ DashStats updated");
  } catch(e){Logger.log("DashStats error: "+e.toString());}
}

/* ══ Triggers ══ */
function onChange(e) {
  ["Quiz","Study","QBank","Notice","Users","Typing","CurriculumStages"].forEach(function(s){try{syncToFirebase(s,s);}catch(ex){}});
  try{updateDashStats();}catch(ex){}
}

function manualSyncAll() {
  ["Quiz","Study","QBank","Notice","Users","Typing","CurriculumStages"].forEach(function(s){try{syncToFirebase(s,s);Logger.log("OK: "+s);}catch(ex){Logger.log("ERR "+s+": "+ex.toString());}});
  try{updateDashStats();Logger.log("✅ DashStats updated");}catch(ex){Logger.log("DashStats ERR: "+ex.toString());}
}

function txt(s){return ContentService.createTextOutput(s).setMimeType(ContentService.MimeType.TEXT);}
function json(o){return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);}

/* ══════════════════════════════════════════════════════════
   BACKUP-ONLY: Firebase → Sheet (read-only)
   এই ফাংশনগুলোর একটাও Firebase-এ কখনো WRITE করে না, শুধু GET/read করে।
   কোনো automatic trigger নেই — Apps Script এডিটরে ফাংশন বেছে ▶ Run চেপে
   ম্যানুয়ালি চালাতে হবে।
══════════════════════════════════════════════════════════ */
// ⛔ Quiz/QBank/Study এখন Sheet-only (source of truth = Sheet, Firebase-এ এই ডেটা
// আর মিরর হয় না) — তাই Firebase → Sheet pull করাটা এখন উল্টো ক্ষতিকর (পুরনো/খালি
// Firebase ডেটা দিয়ে Sheet ওভাররাইট করে দিতে পারে)। তাই এই ৪টা ফাংশন এখন সচেতনভাবে
// no-op, শুধু Logger-এ কারণ জানায়। দরকার হলে (নতুন ফিচার হিসেবে) সরাসরি pullFirebaseToSheet_()
// ম্যানুয়ালি কল করা যাবে, কিন্তু এখন থেকে এটা কখনো Quiz/QBank/Study-এর জন্য অটো-চলবে না।
function backupFirebaseToSheet_Quiz()  { Logger.log("⛔ স্কিপড: Quiz এখন Sheet-only, Firebase-এ ডেটা নেই।"); }
function backupFirebaseToSheet_QBank() { Logger.log("⛔ স্কিপড: QBank এখন Sheet-only, Firebase-এ ডেটা নেই।"); }
function backupFirebaseToSheet_Study() { Logger.log("⛔ স্কিপড: Study এখন Sheet-only, Firebase-এ ডেটা নেই।"); }

function backupFirebaseToSheet_All() {
  Logger.log("⛔ স্কিপড: Quiz/QBank/Study এখন Sheet-only, Firebase-এ ডেটা নেই। pullFirebaseToSheet_() এখন কোনো auto/named ফাংশন থেকে কল হয় না।");
}

function pullFirebaseToSheet_(sheetName) {
  var cfg = getProps();
  var ss  = SpreadsheetApp.getActiveSpreadsheet();
  var sh  = ss.getSheetByName(sheetName);
  if (!sh) { Logger.log("Sheet not found: " + sheetName); return; }

  var url  = cfg.FIREBASE_URL + sheetName + ".json?auth=" + cfg.SECRET_KEY;
  var resp = UrlFetchApp.fetch(url, { muteHttpExceptions: true });
  if (resp.getResponseCode() !== 200) {
    Logger.log("Firebase read ব্যর্থ (" + sheetName + "): " + resp.getContentText());
    return;
  }

  var raw = JSON.parse(resp.getContentText());
  if (!raw) { Logger.log("Firebase-এ কোনো ডেটা নেই: " + sheetName); return; }

  var lastCol = sh.getLastColumn();
  var headerRow = lastCol > 0 ? sh.getRange(1, 1, 1, lastCol).getValues()[0] : [];
  if (headerRow.length === 0) { Logger.log("Header ফাঁকা, আগে header বসাও: " + sheetName); return; }

  var keys = Array.isArray(raw) ? raw.map(function(_, i){ return i; }) : Object.keys(raw);
  var rows = [];
  keys.forEach(function(k) {
    var rec = raw[k];
    if (!rec || typeof rec !== "object") return;
    var row = headerRow.map(function(h) {
      var hh = h.toString().trim();
      if (!hh) return "";
      if (rec.hasOwnProperty(hh)) return rec[hh];
      var lower = hh.toLowerCase();
      for (var rk in rec) { if (rk.toLowerCase() === lower) return rec[rk]; }
      return "";
    });
    rows.push(row);
  });

  var lastRow = sh.getLastRow();
  if (lastRow > 1) sh.getRange(2, 1, lastRow - 1, sh.getLastColumn()).clearContent();
  if (rows.length > 0) sh.getRange(2, 1, rows.length, headerRow.length).setValues(rows);

  Logger.log("✅ " + sheetName + " ব্যাকআপ সম্পন্ন — " + rows.length + " রো (Firebase → Sheet, read-only)।");
}
function checkExamDiag() {
  var raw = PropertiesService.getScriptProperties().getProperty("LAST_EXAM_APPEARANCES_DIAG");
  Logger.log(raw || "❌ কোনো ডেটা সেভ হয়নি");
}
function testUpdateFieldsDirectly() {
  var testId = "QB-00117";
  var testSheet = "QBank";

  var fakeEvent = {
    postData: {
      contents: JSON.stringify({
        secret: PropertiesService.getScriptProperties().getProperty("SECRET_KEY"),
        type: "update_fields",
        sheet: testSheet,
        id: testId,
        fields: { explanation: "🔬DIAG_TEST_" + Date.now() },
        editSource: "DiagnosticTest"
      })
    },
    parameter: {}
  };

  var result = doPost(fakeEvent);
  Logger.log("RESULT: " + result.getContent());
} 
