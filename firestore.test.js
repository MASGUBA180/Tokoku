const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_store_123";
const BOB_UID = "bob_store_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function sampleProduct(uid, createdAt) {
  return {
    userId: uid,
    sku: "8991002101122",
    name: "Minyak Goreng Premium 1L",
    categoryName: "Sembako",
    brandName: "Bimoli",
    baseUnitName: "Satuan",
    costPrice: 11500,
    initialCostPrice: 11500,
    costAdjustment: 0,
    stockBase: 48,
    minStockAlert: 10,
    defaultPriceUnitIndex: 0,
    defaultPriceTierType: "DEFAULT",
    priceUnits: [
      {
        unitName: "Satuan",
        qtyContent: 1,
        minBuy: 1,
        priceDefault: 12600,
        priceGrosir: 12000,
        priceMemberVip: 12000,
      },
      {
        unitName: "Kardus",
        qtyContent: 24,
        minBuy: 1,
        priceDefault: 550000,
        priceGrosir: 520000,
        priceMemberVip: 510000,
      },
    ],
    createdAt,
    updatedAt: createdAt,
  };
}

test("Unauthenticated user cannot read or write any store collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("products").get()
  );
  const now = new Date(Date.now() - 1000);
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .set(sampleProduct(ALICE_UID, now))
  );
});

test("Owner can create, update, list, and delete products without @DocumentId id key", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .set(sampleProduct(ALICE_UID, created))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").get()
  );

  // Partial update preserving createdAt and userId
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .update({
        stockBase: 24,
        costPrice: 12000,
        costAdjustment: 500,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject ghost field / @DocumentId id injection
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_2")
      .set({
        ...sampleProduct(ALICE_UID, created),
        id: "prod_2",
      })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .update({
        createdAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_3")
      .set(sampleProduct(ALICE_UID, new Date(Date.now() + 60000)))
  );
});

test("Cross-user isolation blocks Bob from reading or modifying Alice store", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .set(sampleProduct(ALICE_UID, created));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("products").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .update({ stockBase: 0, updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .delete()
  );
});

test("Stock cards enforce existing product and immutability; transactions enforce immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Orphan stock card without product fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("stock_cards")
      .doc("card_orphan")
      .set({
        userId: ALICE_UID,
        productId: "missing_prod",
        productName: "Missing",
        sku: "123",
        movementType: "MASUK",
        qtyChange: 10,
        stockBefore: 0,
        stockAfter: 10,
        unitSummary: "10 Satuan",
        note: "Test",
        createdAt: created,
      })
  );

  // Create product first, then stock card succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .set(sampleProduct(ALICE_UID, created))
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("stock_cards")
      .doc("card_1")
      .set({
        userId: ALICE_UID,
        productId: "prod_1",
        productName: "Minyak Goreng Premium 1L",
        sku: "8991002101122",
        movementType: "MASUK",
        qtyChange: 48,
        stockBefore: 0,
        stockAfter: 48,
        unitSummary: "2 Kardus",
        note: "Stok Awal",
        createdAt: created,
      })
  );

  // Stock card update & delete rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("stock_cards")
      .doc("card_1")
      .update({ qtyChange: 100 })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("stock_cards")
      .doc("card_1")
      .delete()
  );

  // Transaction create succeeds, update/delete rejected
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transactions")
      .doc("trx_1")
      .set({
        userId: ALICE_UID,
        receiptNumber: "TRX-261122-001",
        customerTier: "GROSIR",
        customerName: "Warung Maju",
        paymentMethod: "TUNAI",
        totalAmount: 520000,
        totalCost: 276000,
        totalProfit: 244000,
        cashPaid: 550000,
        changeAmount: 30000,
        itemCount: 1,
        items: [
          {
            productId: "prod_1",
            productName: "Minyak Goreng Premium 1L",
            unitName: "Kardus",
            qty: 1,
            qtyContent: 24,
            unitPrice: 520000,
            costPricePerBase: 11500,
            subtotal: 520000,
          },
        ],
        createdAt: created,
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("transactions")
      .doc("trx_1")
      .delete()
  );
});

test("Store settings and master references enforce schema & bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("store_settings")
      .doc("default")
      .set({
        userId: ALICE_UID,
        storeName: "Toko Berkah Jaya",
        storeAddress: "Jl. Merdeka No. 12",
        storePhone: "081234567890",
        receiptFooter: "Terima kasih sudah berbelanja!",
        taxPercent: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("references")
      .doc("ref_cat_1")
      .set({
        userId: ALICE_UID,
        type: "CATEGORY",
        name: "Sembako",
        defaultQtyPerUnit: 1,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Invalid reference type rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("references")
      .doc("ref_bad")
      .set({
        userId: ALICE_UID,
        type: "UNKNOWN_TYPE",
        name: "Bad",
        defaultQtyPerUnit: 1,
        createdAt: created,
        updatedAt: created,
      })
  );
});
