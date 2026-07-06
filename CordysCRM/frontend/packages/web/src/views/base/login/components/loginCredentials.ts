import type { LoginParams } from '@lib/shared/models/system/login';

export interface PrepareLoginPayloadOptions {
  username: string;
  password: string;
  authenticate: string;
  platform: LoginParams['platform'];
  loadPublicKey: () => Promise<string>;
  encrypt: (value: string, publicKey: string) => string | false;
}

export async function prepareLoginPayload({
  username,
  password,
  authenticate,
  platform,
  loadPublicKey,
  encrypt,
}: PrepareLoginPayloadOptions): Promise<LoginParams> {
  const publicKey = await loadPublicKey();

  if (!publicKey) {
    throw new Error('LOGIN_PUBLIC_KEY_UNAVAILABLE');
  }

  return {
    username: encrypt(username, publicKey) || '',
    password: encrypt(password, publicKey) || '',
    authenticate,
    platform,
  };
}
