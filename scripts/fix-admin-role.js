// ════════════════════════════════════════════════════════════════
// fix-admin-role.js
// ════════════════════════════════════════════════════════════════
// ONE-TIME FIX — Users/{phone}/Role ফিল্ড কোনো এক signup-loop-এ default
// "User"-এ রিসেট হয়ে গিয়েছিল। এটা Admin SDK দিয়ে সরাসরি লিখে দেয় (rules
// এবং Console-এর কোনো UI restriction বাইপাস করে, কারণ Admin SDK rules
// মানে না)।
//
// env var TARGET_PHONE দিয়ে কোন phone-এর Role বদলাবে বলে দাও (ডিফল্ট:
// 01788196143)। TARGET_ROLE দিয়ে কী বসাবে বলে দাও (ডিফল্ট: "Admin")।

const admin = require('firebase-admin');

async function main() {
  const projectId   = process.env.FIREBASE_ADMIN_PROJECT_ID;
  const clientEmail = process.env.FIREBASE_ADMIN_CLIENT_EMAIL;
  const privateKey  = (process.env.FIREBASE_ADMIN_PRIVATE_KEY || '').replace(/\\n/g, '\n');
  const databaseURL = process.env.FIREBASE_DATABASE_URL;
  const phone       = (process.env.TARGET_PHONE || '01788196143').trim();
  const newRole     = (process.env.TARGET_ROLE || 'Admin').trim();

  if (!projectId || !clientEmail || !privateKey || !databaseURL) {
    console.error('❌ FIREBASE_ADMIN_PROJECT_ID / CLIENT_EMAIL / PRIVATE_KEY / FIREBASE_DATABASE_URL missing');
    process.exit(1);
  }

  admin.initializeApp({
    credential: admin.credential.cert({ projectId, clientEmail, privateKey }),
    databaseURL,
  });

  const db = admin.database();
  const ref = db.ref(`Users/${phone}`);

  const snap = await ref.once('value');
  if (!snap.exists()) {
    console.error(`❌ Users/${phone} নোড পাওয়া যায়নি — phone number ঠিক আছে কিনা চেক করো`);
    process.exit(1);
  }

  const before = snap.val();
  console.log(`বর্তমান Role: "${before.Role || before.role || '(নেই)'}"  (Name: ${before.Name || '?'})`);

  await ref.child('Role').set(newRole);

  console.log(`✅ Users/${phone}/Role → "${newRole}" লেখা হয়ে গেছে।`);
  console.log('ℹ️  অ্যাপে আবার logout → login করলে Admin মেনু দেখা যাবে।');
  process.exit(0);
}

main().catch((err) => {
  console.error('❌ Failed:', err.message);
  process.exit(1);
});
