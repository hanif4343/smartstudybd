// ════════════════════════════════════════════════════════════════
// backfill-uid-to-phone.js
// ════════════════════════════════════════════════════════════════
// ONE-TIME REPAIR SCRIPT — নতুন (tightened) Realtime Database rules
// চালু হওয়ার পর অনেক পুরনো user-এর UidToPhone/{uid} mapping লেখা হয়নি
// (কারণ পুরনো googleSignIn() flow পুরো Users node স্ক্যান করতে চেষ্টা
// করতো, যা নতুন rules-এ আর readable না — তাই match/mapping fail করেছে)।
//
// এই script Admin SDK দিয়ে চলে বলে rules বাইপাস করে সরাসরি সব ডেটা
// পড়তে/লিখতে পারে। এটা করে:
//   1) Firebase Auth-এর সব user list করে (listUsers)
//   2) প্রতিটার email দেখে বুঝে নেয় সেটা phone+password (synthetic
//      email, ...@smartstudybd.app) নাকি real Google email
//   3) Realtime Database-এর Users node-এ মিলিয়ে সঠিক phone বের করে
//      (synthetic হলে email থেকেই phone বেরিয়ে আসে; real Google email
//      হলে Users/*/Email ফিল্ড মিলিয়ে খোঁজে)
//   4) UidToPhone/{uid} = phone লিখে দেয় (শুধু যেগুলো এখনো নেই বা ভুল,
//      সেগুলোই আপডেট করে — বিদ্যমান সঠিক mapping touch করে না)
//
// চালানোর আগে DRY_RUN=true দিয়ে একবার দেখে নাও কী কী change হতে যাচ্ছে,
// তারপর DRY_RUN=false (বা env var বাদ দিয়ে) দিয়ে আসল লেখাটা করো।
//
// লাগবে (GitHub Actions secrets — আগে থেকেই সেট করা আছে set-admin-claim
// এর জন্য, নতুন কিছু লাগবে না):
//   FIREBASE_ADMIN_PROJECT_ID, FIREBASE_ADMIN_CLIENT_EMAIL,
//   FIREBASE_ADMIN_PRIVATE_KEY, FIREBASE_DATABASE_URL

const admin = require('firebase-admin');

const AUTH_EMAIL_DOMAIN = 'smartstudybd.app';

function normalizePhoneLocalBD(raw) {
  let p = (raw || '').trim().replace(/[\s-]/g, '').replace(/^\+/, '');
  if (p.startsWith('880')) p = '0' + p.slice(3);
  return p;
}

/** synthetic email হলে তার ভেতর থেকে phone বের করে, নাহলে null */
function phoneFromSyntheticEmail(email) {
  if (!email || !email.endsWith(`@${AUTH_EMAIL_DOMAIN}`)) return null;
  const local = email.split('@')[0];
  const phone = normalizePhoneLocalBD(local);
  return phone.length === 11 && phone.startsWith('0') ? phone : null;
}

async function main() {
  const projectId    = process.env.FIREBASE_ADMIN_PROJECT_ID;
  const clientEmail  = process.env.FIREBASE_ADMIN_CLIENT_EMAIL;
  const privateKey   = (process.env.FIREBASE_ADMIN_PRIVATE_KEY || '').replace(/\\n/g, '\n');
  const databaseURL  = process.env.FIREBASE_DATABASE_URL;
  const dryRun       = String(process.env.DRY_RUN || 'true').toLowerCase() !== 'false';

  if (!projectId || !clientEmail || !privateKey || !databaseURL) {
    console.error('❌ FIREBASE_ADMIN_PROJECT_ID / CLIENT_EMAIL / PRIVATE_KEY / FIREBASE_DATABASE_URL missing — GitHub secrets চেক করো');
    process.exit(1);
  }

  admin.initializeApp({
    credential: admin.credential.cert({ projectId, clientEmail, privateKey }),
    databaseURL,
  });

  const db = admin.database();

  console.log(dryRun ? '🔎 DRY RUN — কোনো ডেটা লেখা হবে না, শুধু preview' : '✍️  LIVE RUN — UidToPhone আপডেট হবে');

  // ── Users node পুরোটা একবারে মেমোরিতে আনো (Admin SDK rules ignore করে) ──
  const usersSnap = await db.ref('Users').once('value');
  const usersData = usersSnap.val() || {};

  // email(lowercase) → phone, দ্রুত lookup-এর জন্য
  const emailToPhone = new Map();
  for (const [phoneKey, profile] of Object.entries(usersData)) {
    if (!profile || typeof profile !== 'object') continue;
    const email = (profile.Email || profile.email || '').toString().trim().toLowerCase();
    const storedPhone = (profile.Phone || profile.phone || phoneKey).toString().trim();
    if (email) emailToPhone.set(email, storedPhone);
  }

  const existingMapSnap = await db.ref('UidToPhone').once('value');
  const existingMap = existingMapSnap.val() || {};

  let scanned = 0, matched = 0, alreadyOk = 0, unmatched = 0;
  const unmatchedList = [];
  const updates = {};

  let pageToken;
  do {
    const page = await admin.auth().listUsers(1000, pageToken);
    for (const authUser of page.users) {
      scanned++;
      const uid = authUser.uid;
      const email = (authUser.email || '').trim().toLowerCase();

      let phone = phoneFromSyntheticEmail(email);
      if (!phone) {
        // real Google email → Users node-এ Email মিলিয়ে খোঁজো
        phone = emailToPhone.get(email) || null;
      }

      if (!phone) {
        unmatched++;
        unmatchedList.push(`${uid}  (${email || 'no-email'})`);
        continue;
      }

      if (existingMap[uid] === phone) {
        alreadyOk++;
        continue;
      }

      matched++;
      console.log(`  ${uid}  →  ${phone}   ${existingMap[uid] ? `(ছিল: ${existingMap[uid]}, ঠিক করা হচ্ছে)` : '(নতুন)'}`);
      updates[`UidToPhone/${uid}`] = phone;
    }
    pageToken = page.pageToken;
  } while (pageToken);

  console.log('\n── সারাংশ ──');
  console.log(`মোট Auth user স্ক্যান হয়েছে : ${scanned}`);
  console.log(`আগে থেকেই ঠিক ছিল          : ${alreadyOk}`);
  console.log(`নতুন/সংশোধিত mapping        : ${matched}`);
  console.log(`মিলাতে পারিনি (manual চেক দরকার): ${unmatched}`);
  if (unmatchedList.length) {
    console.log('\nমিলাতে পারিনি এই uid গুলোর জন্য:');
    unmatchedList.forEach((l) => console.log('  - ' + l));
  }

  if (!dryRun && Object.keys(updates).length > 0) {
    await db.ref().update(updates);
    console.log(`\n✅ ${Object.keys(updates).length} টা UidToPhone entry লেখা হয়েছে।`);
  } else if (dryRun) {
    console.log('\nℹ️  এটা dry run ছিল — আসলে লিখতে DRY_RUN=false দিয়ে আবার চালাও।');
  } else {
    console.log('\nℹ️  লেখার মতো কোনো পরিবর্তন নেই।');
  }
}

main().catch((err) => {
  console.error('❌ Failed:', err.message);
  process.exit(1);
});
