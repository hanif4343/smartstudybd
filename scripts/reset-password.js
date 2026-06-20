// ════════════════════════════════════════════════════════════════
// reset-password.js
// ════════════════════════════════════════════════════════════════
// কোনো ইউজারের পাসওয়ার্ড admin হিসেবে রিসেট করে দেয় — "forgot password"
// রিকোয়েস্ট এলে এটা চালিয়ে নতুন পাসওয়ার্ড সেট করে দেওয়া যাবে।
//
// লাগবে (GitHub Actions secrets — set-admin-claim এর জন্য আগেই করা আছে,
// নতুন কিছু লাগবে না):
//   FIREBASE_ADMIN_PROJECT_ID, FIREBASE_ADMIN_CLIENT_EMAIL, FIREBASE_ADMIN_PRIVATE_KEY

const admin = require('firebase-admin');

const AUTH_EMAIL_DOMAIN = 'smartstudybd.app';

function normalizePhoneLocalBD(raw) {
  let p = (raw || '').trim().replace(/[\s-]/g, '').replace(/^\+/, '');
  if (p.startsWith('880')) p = '0' + p.slice(3);
  return p;
}

function resolveLookupEmail(input) {
  const trimmed = (input || '').trim();
  if (trimmed.includes('@')) return trimmed.toLowerCase();
  const localPhone = normalizePhoneLocalBD(trimmed);
  return `${localPhone}@${AUTH_EMAIL_DOMAIN}`;
}

async function main() {
  const projectId    = process.env.FIREBASE_ADMIN_PROJECT_ID;
  const clientEmail  = process.env.FIREBASE_ADMIN_CLIENT_EMAIL;
  const privateKey   = (process.env.FIREBASE_ADMIN_PRIVATE_KEY || '').replace(/\\n/g, '\n');
  const newPassword  = process.env.NEW_PASSWORD || '';

  if (!projectId || !clientEmail || !privateKey) {
    console.error('❌ FIREBASE_ADMIN_PROJECT_ID / CLIENT_EMAIL / PRIVATE_KEY missing — GitHub secrets চেক করো');
    process.exit(1);
  }
  if (!process.env.TARGET_PHONE) {
    console.error('❌ TARGET_PHONE input missing (ফোন নম্বর অথবা email দাও)');
    process.exit(1);
  }
  if (newPassword.length < 6) {
    console.error('❌ নতুন পাসওয়ার্ড কমপক্ষে ৬ অক্ষর হতে হবে');
    process.exit(1);
  }

  const lookupEmail = resolveLookupEmail(process.env.TARGET_PHONE);

  admin.initializeApp({
    credential: admin.credential.cert({ projectId, clientEmail, privateKey }),
  });

  const user = await admin.auth().getUserByEmail(lookupEmail);
  await admin.auth().updateUser(user.uid, { password: newPassword });

  console.log(`✅ ${lookupEmail} (uid: ${user.uid}) — নতুন পাসওয়ার্ড সেট হয়েছে`);
  console.log('ℹ️  ইউজারকে নতুন পাসওয়ার্ডটা জানিয়ে দাও, এটা দিয়েই এখন থেকে লগইন করবে।');
}

main().catch((err) => {
  console.error('❌ Failed:', err.message);
  console.error('   (যদি "no user record" দেখায়, এই নম্বর/email দিয়ে কোনো account নেই)');
  process.exit(1);
});
