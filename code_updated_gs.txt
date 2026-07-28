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

/* ══ ATOMIC ID ══ */
function getNextId(sheetName) {
  var lock = LockService.getScriptLock(); lock.waitLock(15000);
  try {
    var prop=PropertiesService.getScriptProperties(), key="MAX_ID_"+sheetName.toUpperCase();
    var ss=SpreadsheetApp.getActiveSpreadsheet(), sh=ss.getSheetByName(sheetName);
    var cur=parseInt(prop.getProperty(key)||"0");
    if(cur<1000&&sh&&sh.getLastRow()>1){
      var ids=sh.getRange(2,1,sh.getLastRow()-1,1).getValues().map(function(r){return parseInt(r[0])||0;});
      cur=Math.max.apply(null,ids);
    }
    if(cur<1000)cur=1000;
    var next=cur+1; prop.setProperty(key,next.toString()); return next;
  } finally { lock.releaseLock(); }
}

/* ══ DUPLICATE CHECK ══ */
function isDuplicate(sheet, subject, questionText, sub_topic) {
  var data=sheet.getDataRange().getValues(); if(data.length<2)return false;
  var hdr=data[0].map(function(h){return h.toString().toLowerCase().trim();});
  var qIdx=hdr.indexOf("question"),subIdx=hdr.indexOf("subject"),stIdx=hdr.indexOf("sub_topic");
  if(stIdx===-1)stIdx=hdr.indexOf("subtopic");
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
function syncToFirebase(sheetName, folderName) {
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

/* ══════════════════════════════════════════════════════════
   doGet
══════════════════════════════════════════════════════════ */
function doGet(e) {
 try {
  var action = e.parameter.action;
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
    var ss=SpreadsheetApp.getActiveSpreadsheet();
    var shName=e.parameter.sheet||"";
    var shMap={quiz:"Quiz",qbank:"QBank",study:"Study",users:"Users",typing:"Typing"};
    shName=shMap[shName.toLowerCase()]||shName;
    var uSheet=ss.getSheetByName(shName);
    if(!uSheet)return json({result:"error",error:"Sheet not found: "+shName});
    var uRows=uSheet.getDataRange().getValues();
    var uHdr=uRows[0].map(function(h){return h.toString().toLowerCase().trim();});
    var idC=uHdr.indexOf("id"); if(idC===-1)idC=uHdr.indexOf("phone");
    var fld=(e.parameter.field||"").toLowerCase().trim();
    // opt1→Opt1, opt2→Opt2 etc. mapping
    var fldAlias={opt1:"opt1",opt2:"opt2",opt3:"opt3",opt4:"opt4"};
    var fldC=uHdr.indexOf(fld);
    if(fldC===-1){
      // try "opt1" → look for "opt1" OR "option1" columns
      var altMap={"opt1":["opt1","option1"],"opt2":["opt2","option2"],"opt3":["opt3","option3"],"opt4":["opt4","option4"]};
      if(altMap[fld]){
        for(var ai=0;ai<altMap[fld].length;ai++){fldC=uHdr.indexOf(altMap[fld][ai]);if(fldC!==-1)break;}
      }
    }
    if(fldC===-1){for(var fc=0;fc<uHdr.length;fc++){if(uHdr[fc].includes(fld)){fldC=fc;break;}}}
    if(idC===-1||fldC===-1)return json({result:"error",error:"Column not found: "+fld});
    var targetId=(e.parameter.id||"").toString().trim();
    var content=(e.parameter.content||"");
    var ufAtC=uHdr.indexOf("updatedat");
    for(var ur=1;ur<uRows.length;ur++){
      if(uRows[ur][idC].toString().trim()===targetId){
        uSheet.getRange(ur+1,fldC+1).setValue(content);
        if(ufAtC!==-1) uSheet.getRange(ur+1,ufAtC+1).setValue(Date.now());
        syncToFirebase(shName,shName);
        return json({result:"success"});
      }
    }
    return json({result:"error",error:"ID not found: "+targetId});
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
    if(fIdx<0)return json({result:"error",error:"field not found: "+field});

    // Firebase mirror sync-এর জন্য দরকার — updateField-এর মতোই id/updatedAt কলাম বের করা হচ্ছে
    var updColIdx2=-1, idColIdx2=-1;
    for(var uc=0;uc<h3.length;uc++){
      var un=h3[uc].toString().toLowerCase().replace(/\s+/g,"");
      if(un==="updatedat")updColIdx2=uc;
      if(un==="id")idColIdx2=uc;
    }

    var count=0, nowMs=Date.now(), touchedRows=[];
    for(var i3=1;i3<d3.length;i3++){
      if(normalizeFieldValue_(d3[i3][fIdx])===oldV){
        sh3.getRange(i3+1,fIdx+1).setValue(newV);
        if(updColIdx2!==-1) sh3.getRange(i3+1,updColIdx2+1).setValue(nowMs);
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

    return json({result:"success",count:count,field:field,old:oldV,new:newV,firebaseSynced:fbSynced});
  }

  // ── deleteByIds ── ★ delete questions by comma-separated IDs
  if (action==="deleteByIds") {
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
    var deleted=0;
    // Delete from bottom to top to preserve row indices
    for(var i4=d4.length-1;i4>=1;i4--){
      var rowId=idIdx>=0?d4[i4][idIdx].toString():"";
      if(ids.indexOf(rowId)>=0){sh4.deleteRow(i4+1);deleted++;}
    }
    // Firebase already updated directly from app - DO NOT sync (would overwrite with array)
    return json({result:"success",deleted:deleted,sheet:shName2});
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
        var parts=stRaw.indexOf(" > ")!==-1?stRaw.split(" > "):[stRaw,stRaw];
        var topic=parts[0].trim()||"General", st=parts.length>1?parts[1].trim():stRaw;
        if(!out.quiz[sub].topics[topic])out.quiz[sub].topics[topic]={total:0,subtopics:{}};
        out.quiz[sub].topics[topic].total++;
        if(!out.quiz[sub].topics[topic].subtopics[st])out.quiz[sub].topics[topic].subtopics[st]={total:0,mcq:0,written:0};
        out.quiz[sub].topics[topic].subtopics[st].total++;
        if(isWr)out.quiz[sub].topics[topic].subtopics[st].written++;else out.quiz[sub].topics[topic].subtopics[st].mcq++;
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
        var btop=(bTopI!==-1?bData[j][bTopI]:"").toString().trim()||"General";
        var bst=(bStI!==-1?bData[j][bStI]:"").toString().trim()||"General";
        var bIsWr=btyp.toLowerCase()==="written";
        if(!out.qbank[bsub])out.qbank[bsub]={total:0,mcq:0,written:0,topics:{}};
        out.qbank[bsub].total++;if(bIsWr)out.qbank[bsub].written++;else out.qbank[bsub].mcq++;
        if(!out.qbank[bsub].topics[btop])out.qbank[bsub].topics[btop]={total:0,subtopics:{}};
        out.qbank[bsub].topics[btop].total++;
        if(!out.qbank[bsub].topics[btop].subtopics[bst])out.qbank[bsub].topics[btop].subtopics[bst]={total:0,mcq:0,written:0};
        out.qbank[bsub].topics[btop].subtopics[bst].total++;
        if(bIsWr)out.qbank[bsub].topics[btop].subtopics[bst].written++;else out.qbank[bsub].topics[btop].subtopics[bst].mcq++;
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
    var grMap={quiz:"Quiz",qbank:"QBank",study:"Study",typing:"Typing",users:"Users",notice:"Notice",reports:"Reports"};
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
      var targetSheetName = params.targetSheet || "Quiz";
      var qcSh = ss.getSheetByName(targetSheetName);
      if (!qcSh) return json({ result: "error", error: "Sheet not found: " + targetSheetName });

      var rows = params.rows || [];
      var added = 0, skipped = 0, errors = [];

      rows.forEach(function(r) {
        try {
          if (isDuplicate(qcSh, r.subject || '', r.question || '', r.sub_topic || '')) {
            skipped++;
            return;
          }
          var newId = getNextId(targetSheetName);
          var rowData = [
            newId,
            r.question || '',
            r.opt1 || '', r.opt2 || '', r.opt3 || '', r.opt4 || '',
            r.correct || '',
            r.subject || '',
            r.sub_topic || '',
            r.explanation || '',
            r.technique || '',
            r.prevExam || '',              // QBank-এর মূল exam paper-এর নাম এখানে থাকবে
            r.qType || 'MCQ',
            r.timestamp || new Date().toLocaleString('bn-BD'),
            r.audienceTags || 'Job',
            Date.now(),
            "NF"
          ];
          qcSh.appendRow(rowData);
          added++;
        } catch (rowErr) {
          errors.push({ q: (r.question || '').substring(0, 40), err: rowErr.toString() });
        }
      });

      return json({ result: "success", added: added, skipped: skipped, errors: errors });
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
      for(var ur=1;ur<uRows.length;ur++){if(uRows[ur][idC].toString().trim()===params.id.toString().trim()){uSheet.getRange(ur+1,fldC+1).setValue(params.content);if(ueAtC!==-1)uSheet.getRange(ur+1,ueAtC+1).setValue(Date.now());if(!params.bulkMode)syncToFirebase(sName,sName);return txt("Successfully Updated");}}
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

    // ── bulk_save_rows — একসাথে অনেক রো Google Sheet-এ সেভ (Save Location = "Google Sheet"
    //    বেছে নিলে QBank→Quiz কনভার্টার, AI Import/OCR direct-submit, বাল্ক আপলোডার — সবাই এই
    //    endpoint ব্যবহার করে)। প্রতিটা রো আলাদাভাবে duplicate-check হয়, শেষে একবারই Firebase sync হয়। ──
    if(params.type==="bulk_save_rows"){
      var bTab=params.targetTab||params.sheet;
      var bSh=ss.getSheetByName(bTab);
      if(!bSh)return json({result:"error",error:"Sheet not found: "+bTab});
      var bRows=params.rows||[];
      if(!bRows.length) return json({result:"success",added:0,skipped:0});

      // ⚡ ফিক্স: আগে প্রতিটা রো-এর জন্য isDuplicate() পুরো শীট আবার getDataRange() দিয়ে
      //    পড়তো, আর appendRow() আলাদাভাবে কল হতো — কয়েকশো প্রশ্নে এটা শয়ে শয়ে ফুল-শীট রিড
      //    করতো বলে সেভ অস্বাভাবিক ধীর হয়ে যাচ্ছিলো (কখনো ৪৭৯টার জন্য মিনিটের পর মিনিট)।
      //    এখন শীট একবারই পড়া হয়, ডুপ্লিকেট চেক in-memory Set দিয়ে হয়, আর সব নতুন রো
      //    শেষে একটাই setValues() কলে ব্যাচ-লেখা হয়।
      var bData=bSh.getDataRange().getValues();
      var bHdr=bData.length?bData[0].map(function(h){return h.toString().toLowerCase().trim();}):[];
      var bQIdx=bHdr.indexOf("question"), bSubIdx=bHdr.indexOf("subject"), bStIdx=bHdr.indexOf("sub_topic");
      if(bStIdx===-1)bStIdx=bHdr.indexOf("subtopic");
      var bNorm=function(s){return (s||'').toString().toLowerCase().replace(/\s+/g,' ').trim().substring(0,100);};
      var bExisting={};
      if(bQIdx!==-1){
        for(var ber=1;ber<bData.length;ber++){
          var bek=bNorm(bData[ber][bQIdx])+"|"+(bStIdx!==-1?bNorm(bData[ber][bStIdx]):"")+"|"+(bSubIdx!==-1?bNorm(bData[ber][bSubIdx]):"");
          bExisting[bek]=true;
        }
      }

      var bLock=LockService.getScriptLock(); bLock.waitLock(15000);
      var bAdded=0, bSkipped=0;
      try{
        var bProp=PropertiesService.getScriptProperties(), bIdKey="MAX_ID_"+bTab.toUpperCase();
        var bCurId=parseInt(bProp.getProperty(bIdKey)||"0");
        if(bCurId<1000 && bSh.getLastRow()>1){
          var bIdCol=bSh.getRange(2,1,bSh.getLastRow()-1,1).getValues().map(function(r){return parseInt(r[0])||0;});
          bCurId=Math.max.apply(null,[1000].concat(bIdCol));
        }
        if(bCurId<1000)bCurId=1000;

        var bNewRows=[];
        var bNowMs=Date.now();
        for(var bi=0;bi<bRows.length;bi++){
          var row=bRows[bi]||{};
          try{
            var bKey=bNorm(row.question)+"|"+bNorm(row.sub_topic)+"|"+bNorm(row.subject);
            if(row.question && bExisting[bKey]){ bSkipped++; continue; }
            var bId=row.editId||(bCurId+1);
            var bLine=[];
            if(bTab==="Quiz")      bLine=[bId,row.question,row.opt1,row.opt2,row.opt3,row.opt4,row.correct,row.subject,row.sub_topic,row.explanation,row.technique||"",row.prevExam||"",row.qType||"MCQ",row.timestamp||new Date().toLocaleString(),row.audienceTags||"",bNowMs,"NF"];
            else if(bTab==="QBank")bLine=[bId,row.question,row.opt1,row.opt2,row.opt3,row.opt4,row.correct,row.subject,row.topic||"",row.sub_topic,row.explanation,row.technique||"",row.qType||"MCQ",row.mainQpaper||"",row.timestamp||new Date().toLocaleString(),row.audienceTags||"",bNowMs,"NF"];
            else if(bTab==="Study")bLine=[bId,row.subject,row.sub_topic,row.question||"",row.correct||"",row.explanation,row.technique||"",row.timestamp||new Date().toLocaleString(),row.audienceTags||"",row.visualUrl||"",bNowMs,"NF"];
            // ── Typing bulk insert-ও একই সরল schema অনুসরণ করে: id, language, content, updatedAt, NF ──
            else if(bTab==="Typing")bLine=[bId,row.language||"",row.content||"",bNowMs,"NF"];
            if(bLine.length===0){ bSkipped++; continue; }
            if(!row.editId)bCurId++;
            bNewRows.push(bLine);
            bExisting[bKey]=true; // একই ব্যাচে দুইবার একই প্রশ্ন থাকলে দ্বিতীয়টাও বাদ পড়বে
            bAdded++;
          }catch(rowErr){ bSkipped++; }
        }
        if(bNewRows.length){
          bSh.getRange(bSh.getLastRow()+1,1,bNewRows.length,bNewRows[0].length).setValues(bNewRows);
        }
        bProp.setProperty(bIdKey,bCurId.toString());
      } finally { bLock.releaseLock(); }

      var bShouldSync = (params.sync!==undefined) ? !!params.sync : true; // পুরনো কলার (sync ফ্ল্যাগ ছাড়া) থাকলে আগের মতোই প্রতিবার সিঙ্ক হবে, নতুন ফ্রন্টএন্ড শুধু শেষ চাংকেই sync:true পাঠায়
      var bSyncOk = true;
      if(bShouldSync) bSyncOk = syncToFirebase(bTab,bTab);
      return json({result:"success",added:bAdded,skipped:bSkipped,firebaseSynced:bSyncOk});
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

    var mSh=ss.getSheetByName(tTab); if(!mSh)return txt("Sheet not found: "+tTab);
    if(params.question&&isDuplicate(mSh,params.subject||'',params.question,params.sub_topic||''))
      return json({result:"duplicate",message:"এই sub-topic-এ প্রশ্নটি আগে থেকেই আছে"});

    var eId=params.editId, rIdx=-1, mData=mSh.getDataRange().getValues(), finalId=eId;
    if(eId){for(var ei=1;ei<mData.length;ei++){if(mData[ei][0].toString()===eId.toString()){rIdx=ei+1;break;}}}
    if(!eId&&["Quiz","Study","QBank","Typing"].indexOf(tTab)>-1)finalId=getNextId(tTab);

    var rData=[];
    var nowMs=Date.now();
    if(tTab==="Quiz")      rData=[finalId,params.question,params.opt1,params.opt2,params.opt3,params.opt4,params.correct,params.subject,params.sub_topic,params.explanation,params.technique,params.prevExam||"",params.qType,params.timestamp,params.audienceTags||"",nowMs];
    else if(tTab==="QBank")rData=[finalId,params.question,params.opt1,params.opt2,params.opt3,params.opt4,params.correct,params.subject,params.topic,params.sub_topic,params.explanation,params.technique,params.qType,params.mainQpaper||"",params.timestamp,params.audienceTags||"",nowMs];
    else if(tTab==="Study")rData=[finalId,params.subject,params.sub_topic,params.question||"",params.correct||"",params.explanation,params.technique,params.timestamp,params.audienceTags||"",params.visualUrl||"",nowMs];
    // ── Typing ট্যাব-এর headers এখন সহজ: id, language, content, updatedAt —
    //    আগে title/level কলামও ছিল, সেগুলো বাদ দেওয়া হলো (Admin App ও এখন এই
    //    ৪টা ফিল্ডই পাঠাবে)। language: "bn" | "en" ──
    else if(tTab==="Typing")rData=[finalId,params.language||"",params.content||"",nowMs];
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

    // ✅ NF (Not Firebase) স্বয়ংক্রিয় বুককিপিং — sync-এর আগে pessimistically "NF" বসানো
    // হয়, sync সফল হলে মুছে ফেলা হয়। sync ব্যর্থ হলে (যেমন Firebase quota exceeded)
    // NF-ই থেকে যায় — ম্যানুয়ালি মার্ক করার আর দরকার নেই, পরে "sync_nf_rows" অ্যাকশন
    // দিয়ে রিট্রাই করা যাবে।
    var nfIdx=-1;
    if(["Quiz","QBank","Study","Typing"].indexOf(tTab)>-1){
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
  ["Quiz","Study","QBank","Notice","Users","Typing"].forEach(function(s){try{syncToFirebase(s,s);}catch(ex){}});
  try{updateDashStats();}catch(ex){}
}

function manualSyncAll() {
  ["Quiz","Study","QBank","Notice","Users","Typing"].forEach(function(s){try{syncToFirebase(s,s);Logger.log("OK: "+s);}catch(ex){Logger.log("ERR "+s+": "+ex.toString());}});
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
function backupFirebaseToSheet_Quiz()  { pullFirebaseToSheet_("Quiz"); }
function backupFirebaseToSheet_QBank() { pullFirebaseToSheet_("QBank"); }
function backupFirebaseToSheet_Study() { pullFirebaseToSheet_("Study"); }

function backupFirebaseToSheet_All() {
  pullFirebaseToSheet_("Quiz");
  pullFirebaseToSheet_("QBank");
  pullFirebaseToSheet_("Study");
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
