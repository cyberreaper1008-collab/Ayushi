const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_jarvis_123";
const BOB_UID = "bob_jarvis_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user cannot read or write any JARVIS data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "Unauthorized",
      details: "",
      category: "TASK",
      priority: "HIGH",
      completed: false,
      dueLabel: "Today",
      streakCount: 0,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: profile create & update succeed; ghost fields & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Tony Stark",
      preferredLanguage: "Hinglish",
      speechSpeed: 1.0,
      speechPitch: 1.0,
      continuousVoice: false,
      wakeWordEnabled: false,
      hudTheme: "ARC_CYAN",
      confirmSensitiveActions: true,
      autoSaveMemories: true,
      notificationsEnabled: true,
      customInstructions: "Be concise.",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "Hindi",
      speechSpeed: 1.25,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot read or write Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "English",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: conversations, tasks, notes, memories, routines, study_logs CRUD & isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 3000);

  // Conversation create (with optional sources)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("msg_1").set({
      userId: ALICE_UID,
      role: "model",
      text: "Good morning, sir. All systems online.",
      mode: "ASSISTANT",
      sources: ["Example|https://example.com"],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject extra `id` field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("msg_2").set({
      id: "msg_2",
      userId: ALICE_UID,
      role: "user",
      text: "Hello",
      mode: "ASSISTANT",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Task create & partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "Revise Electrostatics",
      details: "Solve 20 JEE PYQs",
      category: "STUDY",
      priority: "HIGH",
      completed: false,
      dueLabel: "7:00 PM",
      streakCount: 3,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      completed: true,
      streakCount: 4,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Note create & update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").set({
      userId: ALICE_UID,
      title: "Newton's Laws",
      content: "F = ma, action = -reaction",
      tag: "Physics",
      pinned: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Memory create & update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      keyLabel: "Exam Goal",
      fact: "Preparing for JEE Advanced 2027",
      category: "STUDY",
      approved: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Routine create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("routines").doc("routine_1").set({
      userId: ALICE_UID,
      name: "Morning Briefing",
      triggerPhrase: "start my morning",
      routineType: "MORNING",
      actionSteps: ["Read daily summary", "Check priority tasks", "Open study planner"],
      enabled: true,
      requiresConfirmation: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Study log create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("study_logs").doc("study_1").set({
      userId: ALICE_UID,
      subject: "Physics",
      topic: "Rotational Mechanics",
      examTrack: "JEE",
      masteryPercent: 78,
      questionsSolved: 25,
      correctCount: 20,
      notesSummary: "Moment of inertia theorems mastered.",
      createdAt: now,
      updatedAt: now,
    })
  );

  // List queries succeed on empty & populated subcollections for owner, fail for Bob
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("study_logs").get());
});
