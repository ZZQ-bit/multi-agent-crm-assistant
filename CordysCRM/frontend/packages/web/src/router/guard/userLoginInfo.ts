import { clearToken, hasToken, isLoginExpires } from '@lib/shared/method/auth';

import useUser from '@/hooks/useUser';
import useUserStore from '@/store/modules/user';
import { getFirstRouteNameByPermission } from '@/utils/permission';

import NProgress from 'nprogress';
import type { LocationQueryRaw, Router } from 'vue-router';

export default function setupUserLoginInfoGuard(router: Router) {
  router.beforeEach(async (to, from, next) => {
    NProgress.start();

    const { isWhiteListPage } = useUser();
    const userStore = useUserStore();
    if (isLoginExpires()) {
      clearToken();
    }

    let tokenExists = hasToken();

    // Recover the current session before redirecting in dev/proxy scenarios.
    if (!tokenExists && to.name !== 'login' && !isWhiteListPage()) {
      tokenExists = await userStore.isLogin(true);
    }

    if (!tokenExists && to.name !== 'login' && !isWhiteListPage()) {
      next({
        name: 'login',
        query: {
          redirect: to.name,
          ...to.query,
        } as LocationQueryRaw,
      });
      NProgress.done();
      return;
    }

    if (to.name === 'login' && tokenExists) {
      const userInfo = await userStore.isLogin(true);
      if (userInfo) {
        const firstRoute = getFirstRouteNameByPermission(router.getRoutes());
        next({ name: firstRoute });
        NProgress.done();
        return;
      }
      clearToken();
      tokenExists = false;
    }

    next();
    NProgress.done();
  });
}
