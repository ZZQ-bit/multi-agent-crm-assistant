<template>
  <aside class="history-panel" :class="{ 'history-panel--collapsed': collapsed }">
    <template v-if="collapsed">
      <n-button quaternary circle @click="$emit('toggleCollapse')">
        <template #icon>
          <ChevronForwardOutline />
        </template>
      </n-button>
    </template>
    <template v-else>
      <div class="history-panel__header">
        <div class="history-panel__title">{{ t('aiDealDesk.history') }}</div>
        <n-button quaternary circle @click="$emit('toggleCollapse')">
          <template #icon>
            <ChevronBackOutline />
          </template>
        </n-button>
      </div>
      <n-button class="history-panel__new" secondary type="primary" @click="$emit('newSession')">
        <template #icon>
          <AddOutline />
        </template>
        {{ t('aiDealDesk.newSession') }}
      </n-button>
      <n-scrollbar class="history-panel__scroll">
        <div v-for="group in groupedSessions" :key="group.label" class="history-group">
          <div class="history-group__label">{{ group.label }}</div>
          <button
            v-for="session in group.items"
            :key="session.id"
            type="button"
            class="history-item"
            :class="{ 'history-item--active': session.id === activeSessionId }"
            @click="$emit('selectSession', session.id)"
          >
            <span class="history-item__title">{{ session.title }}</span>
            <span class="history-item__time">{{ session.updatedAt }}</span>
          </button>
        </div>
      </n-scrollbar>
    </template>
  </aside>
</template>

<script setup lang="ts">
  import { NButton, NScrollbar } from 'naive-ui';
  import { AddOutline, ChevronBackOutline, ChevronForwardOutline } from '@vicons/ionicons5';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import type { DealDeskSession } from '../types';

  const props = defineProps<{
    sessions: DealDeskSession[];
    activeSessionId: string;
    collapsed: boolean;
  }>();

  defineEmits<{
    (e: 'selectSession', sessionId: string): void;
    (e: 'newSession'): void;
    (e: 'toggleCollapse'): void;
  }>();

  const { t } = useI18n();

  const groupLabelMap = computed(() => ({
    today: t('aiDealDesk.today'),
    yesterday: t('aiDealDesk.yesterday'),
    earlier: t('aiDealDesk.earlier'),
  }));

  const groupedSessions = computed(() =>
    ['today', 'yesterday', 'earlier']
      .map((group) => ({
        key: group,
        label: groupLabelMap.value[group as keyof typeof groupLabelMap.value],
        items: props.sessions.filter((session) => session.group === group),
      }))
      .filter((group) => group.items.length > 0)
  );
</script>

<style scoped lang="less">
  .history-panel {
    display: flex;
    flex-direction: column;
    width: 320px;
    min-width: 320px;
    height: 100%;
    padding: 34px 28px;
    border-right: 1px solid #d9e4eb;
    background: rgb(255 255 255 / 88%);
    transition: width 0.2s ease, min-width 0.2s ease;
    &--collapsed {
      width: 72px;
      min-width: 72px;
      align-items: center;
      padding: 16px 12px;
    }
  }

  .history-panel__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 26px;
  }

  .history-panel__title {
    font-size: 24px;
    font-weight: 600;
    color: var(--text-n1);
    line-height: 32px;
  }

  .history-panel__new {
    width: 100%;
    height: 48px;
    margin-bottom: 28px;
    border-radius: 8px;
    color: #0ea5c2;
    background: #d9f5f8;
  }

  .history-panel__scroll {
    flex: 1;
    min-height: 0;
  }

  .history-group + .history-group {
    margin-top: 24px;
  }

  .history-group__label {
    margin-bottom: 12px;
    font-size: 14px;
    color: var(--text-n4);
    line-height: 22px;
  }

  .history-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    width: 100%;
    min-height: 64px;
    margin-bottom: 12px;
    padding: 16px 14px;
    border: 1px solid #dfe7ee;
    border-radius: 8px;
    background: var(--text-n10);
    text-align: left;
    transition: all 0.2s ease;
    &:hover {
      border-color: var(--primary-4);
      background: var(--primary-7);
    }
    &--active {
      border-color: #27c3df;
      background: #f1fdff;
    }
  }

  .history-item__title {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--text-n1);
    font-size: 16px;
    line-height: 24px;
  }

  .history-item__time {
    flex-shrink: 0;
    font-size: 14px;
    color: var(--text-n4);
    line-height: 22px;
  }
</style>
