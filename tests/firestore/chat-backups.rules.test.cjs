const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc } = require('firebase/firestore');

const projectId = 'demo-bamachat';
let testEnvironment;

test.before(async () => {
  testEnvironment = await initializeTestEnvironment({
    projectId,
    firestore: {
      rules: fs.readFileSync(path.join(__dirname, '..', '..', 'firestore.rules'), 'utf8'),
    },
  });
});

test.beforeEach(async () => {
  await testEnvironment.clearFirestore();
});

test.after(async () => {
  await testEnvironment.cleanup();
});

test('owner can write and read own chat backup', async () => {
  const ownerDb = testEnvironment.authenticatedContext('owner-user').firestore();
  const backupRef = doc(ownerDb, 'users/owner-user/chat_backups/backup-1');

  await assertSucceeds(setDoc(backupRef, { conversationCount: 1 }));
  await assertSucceeds(getDoc(backupRef));
});

test('different authenticated user cannot read or write another backup', async () => {
  const otherDb = testEnvironment.authenticatedContext('other-user').firestore();
  const backupRef = doc(otherDb, 'users/owner-user/chat_backups/backup-1');

  await assertFails(setDoc(backupRef, { conversationCount: 1 }));
  await assertFails(getDoc(backupRef));
});

test('unauthenticated user cannot read or write a backup', async () => {
  const anonymousDb = testEnvironment.unauthenticatedContext().firestore();
  const backupRef = doc(anonymousDb, 'users/owner-user/chat_backups/backup-1');

  await assertFails(setDoc(backupRef, { conversationCount: 1 }));
  await assertFails(getDoc(backupRef));
});

test('existing protected chat conversation path remains owner-only', async () => {
  const ownerDb = testEnvironment.authenticatedContext('owner-user').firestore();
  const otherDb = testEnvironment.authenticatedContext('other-user').firestore();
  const anonymousDb = testEnvironment.unauthenticatedContext().firestore();
  const ownerRef = doc(ownerDb, 'users/owner-user/chat_conversations/conversation-1');
  const otherRef = doc(otherDb, 'users/owner-user/chat_conversations/conversation-1');
  const anonymousRef = doc(anonymousDb, 'users/owner-user/chat_conversations/conversation-1');

  await assertSucceeds(setDoc(ownerRef, { title: 'Owner' }));
  await assertFails(setDoc(otherRef, { title: 'Other' }));
  await assertFails(getDoc(otherRef));
  await assertFails(getDoc(anonymousRef));
});
