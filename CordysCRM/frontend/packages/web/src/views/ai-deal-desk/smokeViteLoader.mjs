import { createServer } from 'vite';

let smokeServer;

export async function loadSmokeModule(modulePath) {
  if (!smokeServer) {
    smokeServer = await createServer({
      appType: 'custom',
      configFile: false,
      logLevel: 'silent',
      root: process.cwd(),
      server: {
        middlewareMode: true,
      },
    });
  }

  const normalizedPath = modulePath.startsWith('/') ? modulePath : `/${modulePath.replaceAll('\\', '/')}`;
  return smokeServer.ssrLoadModule(normalizedPath);
}

export async function closeSmokeServer() {
  if (!smokeServer) {
    return;
  }

  await smokeServer.close();
  smokeServer = undefined;
}
