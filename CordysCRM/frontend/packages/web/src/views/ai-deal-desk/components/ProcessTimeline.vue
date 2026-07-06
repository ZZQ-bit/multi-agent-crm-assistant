<template>
  <div class="process-block">
    <button type="button" class="process-summary" :class="`process-summary--${summaryStatus}`" @click="$emit('toggle')">
      <div class="process-summary__left">
        <component :is="summaryIcon" class="process-summary__icon" />
        <span class="process-summary__label">{{ t('aiDealDesk.process') }}</span>
        <span class="process-summary__text">{{ process.summary }}</span>
      </div>
      <component :is="process.expanded ? ChevronUpOutline : ChevronDownOutline" class="process-summary__arrow" />
    </button>
    <div v-if="process.expanded" class="process-events">
      <div v-for="event in process.events" :key="event.id" class="process-event">
        <component
          :is="event.status === 'running' ? ReloadOutline : statusIconByStatus[event.status]"
          class="process-event__icon"
          :class="[
            `process-event__icon--${event.status}`,
            { 'process-event__icon--spinning': event.status === 'running' },
          ]"
        />
        <span class="process-event__text">{{ event.text }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import {
    AlertCircleOutline,
    CheckmarkCircleOutline,
    ChevronDownOutline,
    ChevronUpOutline,
    CloseCircleOutline,
    ReloadOutline,
    TimeOutline,
  } from '@vicons/ionicons5';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import type { DealDeskProcessBlock } from '../types';

  const props = defineProps<{
    process: DealDeskProcessBlock;
  }>();

  defineEmits<{
    (e: 'toggle'): void;
  }>();

  const { t } = useI18n();
  const statusIconByStatus = {
    completed: CheckmarkCircleOutline,
    running: ReloadOutline,
    warning: AlertCircleOutline,
    failed: CloseCircleOutline,
  };
  const processStatusPriority = {
    completed: 1,
    running: 2,
    warning: 3,
    failed: 4,
  };

  const summaryStatus = computed(() => {
    return props.process.events.reduce<'completed' | 'running' | 'warning' | 'failed'>((result, event) => {
      return processStatusPriority[event.status] > processStatusPriority[result] ? event.status : result;
    }, 'completed');
  });
  const summaryIcon = computed(() => {
    if (summaryStatus.value === 'running') return TimeOutline;
    return statusIconByStatus[summaryStatus.value];
  });
</script>

<style scoped lang="less">
  .process-block {
    margin: 0 0 12px;
    border: 1px solid #dde8ea;
    border-radius: 14px;
    background: #fcfefe;
  }

  .process-summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    width: 100%;
    padding: 10px 12px;
    color: var(--text-n2);
    text-align: left;
  }

  .process-summary__left {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  .process-summary__icon {
    flex-shrink: 0;
    width: 16px;
    height: 16px;
    font-size: 16px;
    color: #36a269;
  }

  .process-summary__label {
    flex-shrink: 0;
    font-weight: 600;
    color: var(--text-n1);
  }

  .process-summary__text {
    min-width: 0;
    overflow: hidden;
    color: var(--text-n3);
    font-size: 13px;
    line-height: 20px;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .process-summary__arrow {
    flex-shrink: 0;
    width: 16px;
    height: 16px;
    font-size: 16px;
    color: var(--text-n4);
  }

  .process-events {
    padding: 0 12px 12px;
  }

  .process-event {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding-top: 8px;
  }

  .process-event__icon {
    flex-shrink: 0;
    width: 15px;
    height: 15px;
    margin-top: 3px;
    color: var(--text-n6);
    &--completed {
      color: #36a269;
    }
    &--running {
      color: #61a6b3;
    }
    &--warning {
      color: #d88931;
    }
    &--failed {
      color: #d95f59;
    }
    &--spinning {
      animation: process-event-spin 1s linear infinite;
    }
  }

  .process-summary--running .process-summary__icon {
    color: #61a6b3;
  }

  .process-summary--warning .process-summary__icon {
    color: #d88931;
  }

  .process-summary--failed .process-summary__icon {
    color: #d95f59;
  }

  .process-event__text {
    color: var(--text-n2);
    line-height: 22px;
  }

  @keyframes process-event-spin {
    from {
      transform: rotate(0deg);
    }

    to {
      transform: rotate(360deg);
    }
  }
</style>
