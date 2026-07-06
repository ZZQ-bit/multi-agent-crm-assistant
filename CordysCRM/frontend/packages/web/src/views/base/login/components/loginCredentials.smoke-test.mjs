import assert from 'node:assert/strict';

import { prepareLoginPayload } from './loginCredentials.ts';

const prepared = await prepareLoginPayload({
  username: 'admin',
  password: 'CordysCRM',
  authenticate: 'LOCAL',
  platform: 'WEB',
  loadPublicKey: async () => 'public-key-1',
  encrypt: (value, publicKey) => `${publicKey}:${value}`,
});

assert.deepEqual(prepared, {
  username: 'public-key-1:admin',
  password: 'public-key-1:CordysCRM',
  authenticate: 'LOCAL',
  platform: 'WEB',
});

let encryptCalled = false;

await assert.rejects(
  () =>
    prepareLoginPayload({
      username: 'admin',
      password: 'CordysCRM',
      authenticate: 'LOCAL',
      platform: 'WEB',
      loadPublicKey: async () => '',
      encrypt: () => {
        encryptCalled = true;
        return 'unexpected';
      },
    }),
  /LOGIN_PUBLIC_KEY_UNAVAILABLE/
);

assert.equal(encryptCalled, false);

console.log('loginCredentials smoke test passed');
