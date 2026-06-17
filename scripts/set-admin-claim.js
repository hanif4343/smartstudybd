// ════════════════════════════════════════════════════════════════
// set-admin-claim.js
// ════════════════════════════════════════════════════════════════
// একটা ফোন নম্বরের Firebase Auth user কে admin custom claim দেয় (বা বাতিল
// করে)। এই script শুধু GitHub Actions এর ভেতরে চলে — কোনো credential
// app এর কোডে/APK তে যায় না।
//
// লাগবে (GitHub Actions secrets, repo এ আগে থেকে add করা থাকতে হবে):
//   FIREBASE_ADMIN_PROJECT_ID, FIREBASE_ADMIN_CLIENT_EMAIL, FIREBASE_ADMIN_PRIVATE_KEY
//   (এগুলো একটা service account থেকে আসবে — role: "Firebase Authentication Admin" শুধু)
//
// নোট: এই script শুধু সেই ইউজারকেই খুঁজে পাবে যিনি অন্তত একবার নতুন
// Phone-OTP login flow দিয়ে app এ sign in করেছেন (Firebase Auth এ তার
// একটা user record তৈরি হয়ে গেছে)।

const admin = require('firebase-admin');

function normalizePhoneBD(raw) {
  let p = (raw || '').trim().replace(/[\s-]/g, '');
  if (p.startsWith('+')) return p;
  if (p.startsWith('880')) return '+' + p;
  if (p.startsWith('0')) return '+880' + p.slice(1);
  return '+880' + p;
}

async function main() {
  const projectId   = process.env.FIREBASE_ADMIN_PROJECT_ID;
  const clientEmail = process.env.FIREBASE_ADMIN_CLIENT_EMAIL;
  const privateKey  = (process.env.FIREBASE_ADMIN_PRIVATE_KEY || '').replace(/\\n/g, '\n');
  const removeAdmin = String(process.env.REMOVE_ADMIN || 'false').toLowerCase() === 'true';
  const phone       = normalizePhoneBD(process.env.TARGET_PHONE);

  if (!projectId || !clientEmail || !privateKey) {
    console.error('❌ FIREBASE_ADMIN_PROJECT_ID / CLIENT_EMAIL / PRIVATE_KEY missing — GitHub secrets চেক করো');
    process.exit(1);
  }
  if (!process.env.TARGET_PHONE) {
    console.error('❌ TARGET_PHONE input missing');
    process.exit(1);
  }

  admin.initializeApp({
    credential: admin.credential.cert({ projectId, clientEmail, privateKey }),
  });

  const user = await admin.auth().getUserByPhoneNumber(phone);
  const newClaims = { ...(user.customClaims || {}) };

  if (removeAdmin) {
    delete newClaims.admin;
  } else {
    newClaims.admin = true;
  }

  await admin.auth().setCustomUserClaims(user.uid, newClaims);

  console.log(`✅ ${phone} (uid: ${user.uid}) → admin = ${!removeAdmin}`);
  console.log('ℹ️  ফোনে পরের বার app খুললে/login করলে এই claim কার্যকর হবে।');
}

main().catch((err) => {
  console.error('❌ Failed:', err.message);
  process.exit(1);
});
