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
    if(!fbSh)return;
    var fbData=fbSh.getDataRange().getValues(); if(fbData.length<2)return;
    var fbHdr=fbData[0];
    if(sheetName==="Reports"){
      var keyedData={};
      for(var i=1;i<fbData.length;i++){
        var rec={}; for(var j=0;j<fbHdr.length;j++){var k=fbHdr[j].toString().trim();if(k){var v=fbData[i][j];rec[k]=(v instanceof Date)?Utilities.formatDate(v,"GMT+6","dd-MM-yyyy HH:mm:ss"):v.toString();}}
        keyedData["row_"+i]=rec;
      }
      UrlFetchApp.fetch(cfg.FIREBASE_URL+folderName+".json?auth="+cfg.SECRET_KEY,{method:"put",contentType:"application/json",payload:JSON.stringify(keyedData)});
      return;
    }
    var jsonData=[];
    for(var i2=1;i2<fbData.length;i2++){
      var rec2={}; for(var j2=0;j2<fbHdr.length;j2++){var k2=fbHdr[j2].toString().trim();if(k2){var v2=fbData[i2][j2];rec2[k2]=(v2 instanceof Date)?Utilities.formatDate(v2,"GMT+6","dd-MM-yyyy HH:mm:ss"):v2;}}
      jsonData.push(rec2);
    }
    UrlFetchApp.fetch(cfg.FIREBASE_URL+folderName+".json?auth="+cfg.SECRET_KEY,{method:"put",contentType:"application/json",payload:JSON.stringify(jsonData)});
  } catch(e){ Logger.log("Firebase Sync Error: "+e.toString()); }
}

/* ══════════════════════════════════════════════════════════
   doGet
══════════════════════════════════════════════════════════ */
function doGet(e) {
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
    var content=decodeURIComponent(e.parameter.content||"");
    for(var ur=1;ur<uRows.length;ur++){
      if(uRows[ur][idC].toString().trim()===targetId){
        uSheet.getRange(ur+1,fldC+1).setValue(content);
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
    var field=decodeURIComponent(e.parameter.field||"subject");
    var oldV=decodeURIComponent(e.parameter.oldVal||e.parameter.old||"");
    var newV=decodeURIComponent(e.parameter.newVal||e.parameter.new||"");
    if(!oldV||!newV)return json({result:"error",error:"missing values"});
    var ss3=SpreadsheetApp.getActiveSpreadsheet(), sh3=ss3.getSheetByName(shName);
    if(!sh3)return json({result:"error",error:"sheet not found: "+shName});
    var d3=sh3.getDataRange().getValues(), h3=d3[0];
    // Find column — support subject/topic/sub_topic
    var fIdx=-1;
    for(var fi=0;fi<h3.length;fi++){
      if(h3[fi].toString().toLowerCase()===field.toLowerCase()){fIdx=fi;break;}
    }
    if(fIdx<0)return json({result:"error",error:"field not found: "+field});
    var count=0;
    for(var i3=1;i3<d3.length;i3++){
      if(d3[i3][fIdx].toString().trim()===oldV.trim()){
        sh3.getRange(i3+1,fIdx+1).setValue(newV);
        count++;
      }
    }
    // For topic rename: also update sub_topic column if it starts with "oldV > ..."
    if(field.toLowerCase()==="topic"||field.toLowerCase()==="sub_topic"){
      var stIdx=-1;
      for(var si=0;si<h3.length;si++){if(h3[si].toString().toLowerCase()==="sub_topic"||h3[si].toString().toLowerCase()==="subtopic"){stIdx=si;break;}}
      if(stIdx!==-1){
        for(var i4=1;i4<d3.length;i4++){
          var stVal=d3[i4][stIdx].toString().trim();
          if(stVal.indexOf(oldV+" > ")===0){
            sh3.getRange(i4+1,stIdx+1).setValue(newV+" > "+stVal.substring(oldV.length+3));
            count++;
          }
        }
      }
    }
    // Firebase already updated directly from app - DO NOT sync (would overwrite with array)
    return json({result:"success",count:count,field:field,old:oldV,new:newV});
  }

  // ── deleteByIds ── ★ delete questions by comma-separated IDs
  if (action==="deleteByIds") {
    var shName2=e.parameter.sheet||"QBank";
    var shMap3={quiz:"Quiz",qbank:"QBank",study:"Study"};
    shName2=shMap3[shName2.toLowerCase()]||shName2;
    var ids=(decodeURIComponent(e.parameter.ids||"")).split(",").map(function(x){return x.trim();}).filter(Boolean);
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
    var uName=decodeURIComponent(e.parameter.name||"কেউ");
    var uPhone=decodeURIComponent(e.parameter.phone||"");
    var extra=decodeURIComponent(e.parameter.extra||"");

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
    var subject=decodeURIComponent(e.parameter.subject||"প্রশ্নটি");
    var qid=e.parameter.questionId||"", qsheet=decodeURIComponent(e.parameter.qsheet||"");
    if(!phone)return json({result:"error",error:"phone missing"});
    var safePhone=phone.replace(/[.#$\[\]\s]/g,'_');
    var payload={type:'report_resolved',title:'✅ রিপোর্ট সমাধান হয়েছে!',body:'"'+subject+'" সংশোধন করা হয়েছে।',questionId:qid,qsheet:qsheet,time:new Date().toLocaleString(),read:false};
    UrlFetchApp.fetch(cfg.FIREBASE_URL+"Notifications/"+safePhone+"/notif_"+Date.now()+".json?auth="+cfg.SECRET_KEY,{method:"put",contentType:"application/json",payload:JSON.stringify(payload),muteHttpExceptions:true});
    return json({result:"success",fcm:sendFCMToPhone(phone,"✅ রিপোর্ট সমাধান!",'"'+subject+'" সংশোধন হয়েছে।',{type:"report_resolved",questionId:qid,url:"report"})});
  }

  // ── personalNotify ──
  if (action==="personalNotify") {
    var phone=(e.parameter.phone||"").toString().replace(/^'+/,'').trim();
    var title=decodeURIComponent(e.parameter.title||"Smart Study");
    var body=decodeURIComponent(e.parameter.body||"");
    if(!phone)return json({result:"error",error:"phone missing"});
    var extraData={type:"personal_notification"};
    var nu=decodeURIComponent(e.parameter.url||"");var nq=decodeURIComponent(e.parameter.questionId||"");var nqs=decodeURIComponent(e.parameter.qsheet||"");
    if(nu)extraData.url=nu;if(nq)extraData.questionId=nq;if(nqs)extraData.qsheet=nqs;
    return json({result:"success",fcm:sendFCMToPhone(phone,title,body,extraData)});
  }

  // ── broadcastNotification ──
  if (action==="broadcastNotification") {
    var title=decodeURIComponent(e.parameter.title||'Smart Study');
    var body=decodeURIComponent(e.parameter.body||'');
    return json({result:"success",fcm:sendFCMToAll(title,body,{type:"broadcast",url:decodeURIComponent(e.parameter.url||'qbank')})});
  }

  // ── postNotice ──
  if (action==="postNotice") {
    var ss=SpreadsheetApp.getActiveSpreadsheet(), nSh=ss.getSheetByName("Notice");
    if(!nSh){nSh=ss.insertSheet("Notice");nSh.appendRow(["Date","Title","Message","Timestamp"]);}
    var nTitle=decodeURIComponent(e.parameter.n_title||""), nMsg=decodeURIComponent(e.parameter.n_msg||"");
    var nTs=decodeURIComponent(e.parameter.timestamp||new Date().toLocaleString());
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
      for(var ur=1;ur<uRows.length;ur++){if(uRows[ur][idC].toString().trim()===params.id.toString().trim()){uSheet.getRange(ur+1,fldC+1).setValue(params.content);syncToFirebase(sName,sName);return txt("Successfully Updated");}}
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

    var mSh=ss.getSheetByName(tTab); if(!mSh)return txt("Sheet not found: "+tTab);
    if(params.question&&isDuplicate(mSh,params.subject||'',params.question,params.sub_topic||''))
      return json({result:"duplicate",message:"এই sub-topic-এ প্রশ্নটি আগে থেকেই আছে"});

    var eId=params.editId, rIdx=-1, mData=mSh.getDataRange().getValues(), finalId=eId;
    if(eId){for(var ei=1;ei<mData.length;ei++){if(mData[ei][0].toString()===eId.toString()){rIdx=ei+1;break;}}}
    if(!eId&&["Quiz","Study","QBank","Typing"].indexOf(tTab)>-1)finalId=getNextId(tTab);

    var rData=[];
    if(tTab==="Quiz")      rData=[finalId,params.question,params.opt1,params.opt2,params.opt3,params.opt4,params.correct,params.subject,params.sub_topic,params.explanation,params.technique,params.prevExam||"",params.qType,params.timestamp,params.audienceTags||""];
    else if(tTab==="QBank")rData=[finalId,params.question,params.opt1,params.opt2,params.opt3,params.opt4,params.correct,params.subject,params.topic,params.sub_topic,params.explanation,params.technique,params.qType,params.mainQpaper||"",params.timestamp,params.audienceTags||""];
    else if(tTab==="Study")rData=[finalId,params.subject,params.sub_topic,params.question||"",params.correct||"",params.explanation,params.technique,params.timestamp,params.audienceTags||"",params.visualUrl||""];
    else if(tTab==="Typing")rData=[finalId,params.title||"",params.language||"",params.level||"",params.content||""];
    else if(tTab==="Notice")rData=[params.timestamp?params.timestamp.split(',')[0]:"",params.n_title,params.n_msg,params.timestamp];
    else if(tTab==="Reports"){
      var phone=(params.Phone||"").toString().replace(/^'+/,'').trim();
      var phoneForSheet=phone?("'"+phone):"";
      rData=[phoneForSheet,params.QSheet||"",params.Subject||"",params.SubTopic||params.Topic||"",params.QuestionID||"",params.Question||"",params.Issue||"",params.Timestamp||params.timestamp||new Date().toLocaleString('bn-BD')];
    }

    if(rData.length===0)return json({result:"error",error:"Unknown tab"});
    if(rIdx!==-1)mSh.getRange(rIdx,1,1,rData.length).setValues([rData]);else mSh.appendRow(rData);
    if(!params.bulkMode)syncToFirebase(tTab,tTab);
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
