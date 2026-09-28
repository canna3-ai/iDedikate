/**
 * Credits rewarded-ad rewards to wallets. AdMob calls this URL (server-side verification) after a
 * user finishes a rewarded ad; the app never writes rewards itself (see firestore.rules).
 *
 * Set this function's URL as the "Server-side verification" callback on each rewarded ad unit in
 * AdMob. https://developers.google.com/admob/android/ssv
 */
const { onRequest } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const crypto = require("node:crypto");

initializeApp();
const db = getFirestore();

const VERIFIER_KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";
const KEYS_MAX_AGE_MS = 24 * 60 * 60 * 1000;

/**
 * What each rewarded ad unit credits, keyed by the ad unit's ID (the part after the "/").
 * Keyed by ad unit rather than the app's custom_data so a modified app can't claim a bigger reward.
 * Must match RewardAdType in the app (RewardedAdHelper.kt).
 */
const REWARDS_BY_AD_UNIT = {
  "5204992073": { field: "tokens", amount: 1 },
  "6326502052": { field: "plaques", amount: 1 },
  "6763731739": { field: "incenseSticks", amount: 5 },
  "2455539800": { field: "flowers", amount: 2 },
  "1912614324": { field: "candles", amount: 2 },
};

/** Must match Wallet.STARTER_BALANCE in the app and the wallet create rule. */
const STARTER_BALANCE = { tokens: 2, plaques: 1, incenseSticks: 4, flowers: 4, candles: 2 };

/** Wallets created before flowers and candles replaced fruit and food (see Wallet.legacyMigration). */
const LEGACY_FIELDS = { flowers: "fruit", candles: "food" };

let verifierKeys = null;
let verifierKeysFetchedAt = 0;

async function getVerifierKeys(forceRefresh) {
  if (!forceRefresh && verifierKeys && Date.now() - verifierKeysFetchedAt < KEYS_MAX_AGE_MS) {
    return verifierKeys;
  }
  const response = await fetch(VERIFIER_KEYS_URL);
  if (!response.ok) throw new Error(`Fetching AdMob verifier keys failed: HTTP ${response.status}`);
  const { keys } = await response.json();
  verifierKeys = new Map(keys.map((key) => [String(key.keyId), key.pem]));
  verifierKeysFetchedAt = Date.now();
  return verifierKeys;
}

/**
 * AdMob signs everything in the query string before "&signature=" (signature and key_id are
 * always the last two parameters) with ECDSA/SHA-256; the signature is web-safe base64 DER.
 */
async function isSignedByAdMob(rawQuery, params) {
  const signatureStart = rawQuery.indexOf("&signature=");
  const signature = params.get("signature");
  const keyId = params.get("key_id");
  if (signatureStart < 0 || !signature || !keyId) return false;

  let keys = await getVerifierKeys(false);
  // Google rotates keys; refetch once if this one is new
  if (!keys.has(keyId)) keys = await getVerifierKeys(true);
  const pem = keys.get(keyId);
  if (!pem) return false;

  const message = Buffer.from(rawQuery.substring(0, signatureStart), "utf8");
  return crypto.verify("sha256", message, pem, Buffer.from(signature, "base64url"));
}

exports.admobRewardCallback = onRequest(async (req, res) => {
  if (req.method !== "GET") {
    res.status(405).send("Method not allowed");
    return;
  }

  const url = req.originalUrl || req.url;
  const queryStart = url.indexOf("?");
  const rawQuery = queryStart >= 0 ? url.substring(queryStart + 1) : "";
  const params = new URLSearchParams(rawQuery);

  let verified;
  try {
    verified = await isSignedByAdMob(rawQuery, params);
  } catch (e) {
    // Non-2xx makes AdMob retry later
    logger.error("Couldn't verify AdMob callback", e);
    res.status(500).send("Verification unavailable");
    return;
  }
  if (!verified) {
    logger.warn("Rejected AdMob callback with an invalid signature", { query: rawQuery });
    res.status(403).send("Invalid signature");
    return;
  }

  const uid = params.get("user_id");
  const transactionId = params.get("transaction_id");
  const adUnitId = (params.get("ad_unit") || "").split("/").pop();
  const reward = REWARDS_BY_AD_UNIT[adUnitId];

  // AdMob's "Verify URL" test sends a signed callback without a user; answer 200 so it passes.
  // Anything else unusable also gets 200, since retrying wouldn't make it usable.
  if (!uid || !transactionId || !reward) {
    logger.warn("Ignored AdMob callback", { uid, transactionId, adUnitId });
    res.status(200).send("Ignored");
    return;
  }

  const walletRef = db.collection("wallets").doc(uid);
  const transactionRef = db.collection("adRewards").doc(transactionId);
  try {
    const credited = await db.runTransaction(async (t) => {
      const [processed, wallet] = await t.getAll(transactionRef, walletRef);
      // AdMob can deliver the same callback more than once
      if (processed.exists) return false;

      t.create(transactionRef, {
        uid,
        adUnit: adUnitId,
        field: reward.field,
        amount: reward.amount,
        creditedAt: FieldValue.serverTimestamp(),
      });

      if (!wallet.exists) {
        // Normally the app creates the wallet at sign-in; start it here if that hasn't landed yet
        t.set(walletRef, {
          ...STARTER_BALANCE,
          [reward.field]: STARTER_BALANCE[reward.field] + reward.amount,
          lastRewardAt: FieldValue.serverTimestamp(),
        });
        return true;
      }

      const data = wallet.data();
      const update = { lastRewardAt: FieldValue.serverTimestamp() };
      // Move any legacy balances across first, so the reward doesn't strand them
      for (const [field, legacyField] of Object.entries(LEGACY_FIELDS)) {
        if (legacyField in data && !(field in data)) {
          update[field] = data[legacyField];
          update[legacyField] = FieldValue.delete();
        }
      }
      update[reward.field] = reward.field in update ?
        update[reward.field] + reward.amount :
        FieldValue.increment(reward.amount);
      t.update(walletRef, update);
      return true;
    });
    logger.info(credited ? "Credited reward" : "Reward already credited", { uid, transactionId, ...reward });
    res.status(200).send("OK");
  } catch (e) {
    logger.error("Failed to credit reward", { uid, transactionId, error: e });
    res.status(500).send("Failed to credit reward");
  }
});
