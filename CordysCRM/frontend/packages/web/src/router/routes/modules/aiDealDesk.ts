import { AiDealDeskRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const aiDealDesk: AppRouteRecordRaw = {
  path: '/ai-deal-desk',
  name: AiDealDeskRouteEnum.AI_DEAL_DESK,
  redirect: '/ai-deal-desk/index',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.aiDealDesk',
    permissions: ['AGENT:READ'],
    icon: 'iconicon_bot',
    collapsedLocale: 'menu.aiDealDesk',
    hideChildrenInMenu: true,
    menuOrder: -1,
  },
  children: [
    {
      path: 'index',
      name: AiDealDeskRouteEnum.AI_DEAL_DESK_INDEX,
      component: () => import('@/views/ai-deal-desk/index.vue'),
      meta: {
        locale: 'menu.aiDealDesk',
        permissions: ['AGENT:READ'],
      },
    },
  ],
};

const aiDealDeskLegacyEntry: AppRouteRecordRaw = {
  path: '/agent/deal-desk',
  name: 'agentDealDeskRedirect',
  redirect: '/ai-deal-desk/index',
  component: DEFAULT_LAYOUT,
  meta: {
    permissions: ['AGENT:READ'],
    hideInMenu: true,
    activeMenu: AiDealDeskRouteEnum.AI_DEAL_DESK,
  },
};

export default [aiDealDesk, aiDealDeskLegacyEntry];
