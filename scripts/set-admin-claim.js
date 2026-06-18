// ════════════════════════════════════════════════════════════════
// set-admin-claim.js
// ════════════════════════════════════════════════════════════════
// একটা ইউজারকে Firebase Auth এ admin custom claim দেয় (বা বাতিল করে)।
// এই script শুধু GitHub Actions এর ভেতরে চলে — কোনো credential app এর
// কোডে/APK তে যায় না।
//
// লাগবে (GitHub Actions secrets, repo এ আগে থেকে add করা থাকতে হবে):
//   FIREBASE_ADMIN_PROJECT_ID, FIREBASE_ADMIN_CLIENT_EMAIL, FIREBASE_ADMIN_PRIVATE_KEY
//   (এগুলো একটা service account থেকে আসবে — role: "Firebase Authentication Admin" শুধু)
//
// ইউজার চিহ্নিত করার দুই উপায় (TARGET_PHONE input এ যেকোনো একটা দিলেই হবে):
//   - ফোন নম্বর (01XXXXXXXXX) → app এর ফোন+পাসওয়ার্ড account খুঁজবে
//     (এর ভেতরে app যে synthetic email বানায় তা দিয়েই খুঁজে, যেমন
//      "01788196143@smartstudybd.app")
//   - সরাসরি email (যেমন someone@gmail.com) → Google দিয়ে সাইন আপ করা
//     account এর জন্য, যাদের কোনো synthetic email নেই, real Google email আছে
//
// নোট: এই script শুধু সেই ইউজারকেই খুঁজে পাবে যিনি অন্তত একবার app এ
// sign in/সাইন আপ করেছেন (Firebase Auth এ তার একটা user record তৈরি হয়ে গেছে)।

const admin = require('firebase-admin');

const AUTH_EMAIL_DOMAIN = 'smartstudybd.app';

function normalizePhoneLocalBD(raw) {
  let p = (raw || '').trim().replace(/[\s-]/g, '').replace(/^\+/, '');
  if (p.startsWith('880')) p = '0' + p.slice(3);
  return p; // প্রত্যাশিত ফলাফল: "01XXXXXXXXX"
}

function resolveLookupEmail(input) {
  const trimmed = (input || '').trim();
  if (trimmed.includes('@')) {
    // ইউজার সরাসরি email দিয়েছে (Google দিয়ে সাইন আপ করা অ্যাকাউন্ট)
    return trimmed.toLowerCase();
  }
  // ফোন নম্বর ধরে নিয়ে synthetic email বানাও (phone+password অ্যাকাউন্ট)
  const localPhone = normalizePhoneLocalBD(trimmed);
  return `${localPhone}@${AUTH_EMAIL_DOMAIN}`;
}

async function main() {
  const projectId   = process.env.FIREBASE_ADMIN_PROJECT_ID;
  const clientEmail = process.env.FIREBASE_ADMIN_CLIENT_EMAIL;
  const privateKey  = (process.env.FIREBASE_ADMIN_PRIVATE_KEY || '').replace(/\\n/g, '\n');
  const removeAdmin = String(process.env.REMOVE_ADMIN || 'false').toLowerCase() === 'true';

  if (!projectId || !clientEmail || !privateKey) {
    console.error('❌ FIREBASE_ADMIN_PROJECT_ID / CLIENT_EMAIL / PRIVATE_KEY missing — GitHub secrets চেক করো');
    process.exit(1);
  }
  if (!process.env.TARGET_PHONE) {
    console.error('❌ TARGET_PHONE input missing (ফোন নম্বর অথবা email দাও)');
    process.exit(1);
  }

  const lookupEmail = resolveLookupEmail(process.env.TARGET_PHONE);

  admin.initializeApp({
    credential: admin.credential.cert({ projectId, clientEmail, privateKey }),
  });

  const user = await admin.auth().getUserByEmail(lookupEmail);
  const newClaims = { ...(user.customClaims || {}) };

  if (removeAdmin) {
    delete newClaims.admin;
  } else {
    newClaims.admin = true;
  }

  await admin.auth().setCustomUserClaims(user.uid, newClaims);

  console.log(`✅ ${lookupEmail} (uid: ${user.uid}) → admin = ${!removeAdmin}`);
  console.log('ℹ️  পরের বার app খুললে/login করলে এই claim কার্যকর হবে।');
}

main().catch((err) => {
  console.error('❌ Failed:', err.message);
  console.error('   (যদি "no user record" দেখায়, ইউজার এখনো একবারও app এ sign in/সাইন আপ করেননি)');
  process.exit(1);
});
