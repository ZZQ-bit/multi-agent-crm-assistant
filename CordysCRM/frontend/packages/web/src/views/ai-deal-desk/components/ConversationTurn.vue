<template>
  <div class="turn" :class="turn.role === 'user' ? 'turn--user' : 'turn--assistant'">
    <div class="turn__content">
      <div v-if="turn.role === 'user'" class="turn-shell turn-shell--user">
        <div class="user-bubble">
          <div v-if="attachmentReferences.length" class="reference-list">
            <button
              v-for="reference in attachmentReferences"
              :key="reference.id"
              type="button"
              class="reference-chip"
              :class="`reference-chip--${reference.type}`"
              @click="$emit('viewReference', reference)"
            >
              <img v-if="reference.url" :src="reference.url" :alt="reference.label" />
              <span>{{ reference.label }}</span>
              <em>{{ t('aiDealDesk.viewImage') }}</em>
            </button>
          </div>
          <div class="turn__text turn__text--user">
            <button
              v-for="reference in inlineReferences"
              :key="reference.id"
              type="button"
              class="reference-pill"
              :class="`reference-pill--${reference.type}`"
              @click="$emit('viewReference', reference)"
            >
              {{ reference.label }}
            </button>
            <span class="turn__text-copy">{{ turn.text }}</span>
          </div>
        </div>
        <div class="turn-actions turn-actions--user">
          <div class="turn-action-group">
            <n-tooltip v-if="canCopyTurn" trigger="hover" :delay="300">
              <template #trigger>
                <button type="button" class="turn-action-button turn-copy-button" :aria-label="t('common.copy')" @click.stop="copyTurnText">
                  <CrmIcon type="iconicon_file_copy" :size="15" />
                </button>
              </template>
              {{ t('common.copy') }}
            </n-tooltip>
          </div>
          <time class="turn__time">{{ turn.time }}</time>
        </div>
      </div>

      <div v-else class="assistant-block">
        <div v-if="turn.status === 'generating' && !turn.text" class="assistant-loading">
          <span></span>
          <span></span>
          <span></span>
        </div>
        <ProcessTimeline v-if="displayProcess" :process="displayProcess" @toggle="toggleProcessPanel" />
        <AssistantMarkdownPreview v-if="turn.text" :turn-id="turn.id" :source="turn.text" />
        <div class="turn-actions turn-actions--assistant">
          <div class="turn-action-group">
            <n-tooltip v-if="canRetryTurn" trigger="hover" :delay="300">
              <template #trigger>
                <button type="button" class="turn-action-button turn-retry-button" :aria-label="t('common.retry')" @click.stop="$emit('retry', turn.id)">
                  <CrmIcon type="iconicon_refresh" :size="15" />
                </button>
              </template>
              {{ t('common.retry') }}
            </n-tooltip>
            <n-tooltip v-if="canCopyTurn" trigger="hover" :delay="300">
              <template #trigger>
                <button type="button" class="turn-action-button turn-copy-button" :aria-label="t('common.copy')" @click.stop="copyTurnText">
                  <CrmIcon type="iconicon_file_copy" :size="15" />
                </button>
              </template>
              {{ t('common.copy') }}
            </n-tooltip>
          </div>
          <time class="turn__time turn__time--assistant">{{ turn.time }}</time>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { NTooltip } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmIcon from '@/components/pure/crm-icon-font/index.vue';
  import useLegacyCopy from '@/hooks/useLegacyCopy';

  import AssistantMarkdownPreview from './AssistantMarkdownPreview.vue';
  import ProcessTimeline from './ProcessTimeline.vue';

  import type { DealDeskReference, DealDeskTurn } from '../types';

  const props = defineProps<{
    turn: DealDeskTurn;
  }>();

  const emit = defineEmits<{
    (e: 'toggleProcess', turnId: string): void;
    (e: 'viewCandidate', reference: DealDeskReference): void;
    (e: 'viewReference', reference: DealDeskReference): void;
    (e: 'retry', turnId: string): void;
  }>();

  const { t } = useI18n();
  const { legacyCopy } = useLegacyCopy();
  const processExpanded = ref(false);
  const processTouched = ref(false);
  const previousStatus = ref<DealDeskTurn['status']>(props.turn.status);

  const displayProcess = computed(() => {
    if (!props.turn.process) return null;
    return {
      ...props.turn.process,
      expanded: processExpanded.value,
    };
  });
  const processSignature = computed(() => {
    return `${props.turn.process?.events.length || 0}:${props.turn.process?.summary || ''}`;
  });
  const inlineReferences = computed(() =>
    (props.turn.references || []).filter(
      (reference) => reference.type === 'customer' || reference.type === 'opportunity'
    )
  );
  const attachmentReferences = computed(() =>
    (props.turn.references || []).filter((reference) => reference.type === 'file')
  );
  const canCopyTurn = computed(() => Boolean(props.turn.text?.trim()));
  const canRetryTurn = computed(() => {
    if (props.turn.role !== 'assistant') return false;
    if (props.turn.status === 'failed') return true;
    const text = props.turn.text || '';
    return text.includes('未返回有效最终回复') || text.includes('请求失败') || text.includes('request failed');
  });

  function syncProcessExpanded(status?: DealDeskTurn['status']) {
    if (!props.turn.process || processTouched.value) return;
    processExpanded.value = status === 'generating';
  }

  function toggleProcessPanel() {
    processTouched.value = true;
    processExpanded.value = !processExpanded.value;
    emit('toggleProcess', props.turn.id);
  }

  function copyTurnText() {
    const text = props.turn.text?.trimEnd() || '';
    if (!text) return;
    legacyCopy(text);
  }

  watch(
    () => props.turn.id,
    () => {
      processTouched.value = false;
      previousStatus.value = props.turn.status;
      syncProcessExpanded(props.turn.status);
    },
    { immediate: true }
  );

  watch(
    () => props.turn.status,
    (status) => {
      if (status !== previousStatus.value) {
        if (!processTouched.value) {
          processExpanded.value = status === 'generating';
        }
        previousStatus.value = status;
        return;
      }
      syncProcessExpanded(status);
    },
    { immediate: true }
  );

  watch(
    () => processSignature.value,
    () => {
      syncProcessExpanded(props.turn.status);
    },
    { immediate: true }
  );
</script>

<style scoped lang="less">
  .turn {
    display: flex;
    margin-bottom: 28px;
    &--user {
      justify-content: flex-end;
    }
  }

  .turn__content {
    width: min(1180px, 100%);
  }

  .turn--user .turn__content {
    display: flex;
    justify-content: flex-end;
    width: auto;
    max-width: min(1180px, 100%);
  }

  .user-bubble {
    display: inline-flex;
    flex-direction: column;
    align-items: flex-end;
    max-width: min(720px, 100%);
    padding: 12px 16px;
    border: 1px solid #bfe6ea;
    border-radius: 20px;
    background: #eef9fa;
  }

  .turn-shell {
    display: inline-flex;
    flex-direction: column;
    max-width: min(720px, 100%);
  }

  .turn-shell--user {
    align-items: flex-end;
  }

  .assistant-block {
    position: relative;
    max-width: 1180px;
    padding-top: 2px;
  }

  .turn-actions {
    display: flex;
    position: relative;
    align-items: center;
    gap: 10px;
    min-height: 22px;
    margin-top: 6px;
    color: var(--text-n4);
    font-size: 12px;
    line-height: 18px;
  }

  .turn-actions--user {
    justify-content: flex-end;
  }

  .turn-actions--assistant {
    justify-content: flex-start;
  }

  .turn-action-group {
    position: absolute;
    z-index: 1;
    top: 2px;
    display: inline-flex;
    align-items: center;
    gap: 8px;
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.16s ease;
  }

  .turn-actions--user .turn-action-group {
    right: 0;
  }

  .turn-actions--assistant .turn-action-group {
    left: 0;
  }

  .turn-action-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border: none;
    border-radius: 4px;
    background: transparent;
    color: var(--text-n4);
    cursor: pointer;
    user-select: none;
    -webkit-user-select: none;
    transition: opacity 0.16s ease, color 0.16s ease, background-color 0.16s ease;
    &:hover {
      background: rgb(39 184 209 / 9%);
      color: #167c8d;
    }
    &:focus-visible {
      opacity: 1;
      pointer-events: auto;
      outline: 2px solid rgb(39 184 209 / 28%);
      outline-offset: 2px;
    }
  }

  :deep(.turn-action-button *) {
    pointer-events: none;
    cursor: inherit;
    user-select: none;
    -webkit-user-select: none;
  }

  .turn-copy-button {
    flex: none;
  }

  .turn-retry-button {
    flex: none;
  }

  .turn-shell:hover .turn-action-button,
  .assistant-block:hover .turn-action-button {
    pointer-events: auto;
  }

  .turn-shell:hover .turn-action-group,
  .assistant-block:hover .turn-action-group {
    opacity: 1;
    pointer-events: auto;
  }

  .turn-shell:hover .turn__time,
  .assistant-block:hover .turn__time {
    opacity: 0;
    pointer-events: none;
  }

  .reference-list {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 6px;
    margin-bottom: 10px;
  }

  .reference-chip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    max-width: 100%;
    padding: 2px 8px;
    border: 1px solid #cfe2e5;
    border-radius: 999px;
    background: var(--text-n10);
    font-size: 12px;
    color: var(--text-n2);
    line-height: 20px;
    em {
      font-style: normal;
      color: var(--text-n4);
    }
    img {
      width: 22px;
      height: 22px;
      flex: none;
      border-radius: 6px;
      object-fit: cover;
      background: #edf4f6;
    }
    span {
      overflow: hidden;
      min-width: 0;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    &--file {
      border-color: var(--text-n7);
      color: var(--text-n2);
    }
  }

  .turn__text {
    margin: 0;
    color: var(--text-n1);
    font-size: 14px;
    line-height: 24px;
    white-space: pre-wrap;
    strong {
      font-weight: 700;
    }
  }

  .turn__text--user {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: flex-end;
    gap: 10px 8px;
    text-align: left;
  }

  .turn__text-copy {
    white-space: pre-wrap;
    word-break: break-word;
  }

  .reference-pill {
    flex: none;
    padding: 1px 9px;
    border: none;
    border-radius: 10px;
    background: rgb(39 184 209 / 12%);
    color: #2295a7;
    font-size: 14px;
    line-height: 24px;
    font-weight: 600;
    transition: background-color 0.2s ease, color 0.2s ease;
    &:hover {
      background: rgb(39 184 209 / 18%);
      color: #157b8c;
    }
    &--opportunity {
      background: rgb(80 126 255 / 10%);
      color: #4669c7;
      &:hover {
        background: rgb(80 126 255 / 16%);
        color: #3552a1;
      }
    }
  }
  .turn__time {
    display: block;
    font-size: 12px;
    color: var(--text-n4);
    line-height: 18px;
    transition: opacity 0.16s ease;
  }

  .turn__time--assistant {
    margin-top: 0;
  }

  .assistant-loading {
    display: inline-flex;
    gap: 6px;
    padding: 10px 0 6px;
    span {
      width: 6px;
      height: 6px;
      border-radius: 999px;
      background: #7a9599;
      animation: blink 1.1s infinite ease-in-out;
    }
    span:nth-child(2) {
      animation-delay: 0.15s;
    }
    span:nth-child(3) {
      animation-delay: 0.3s;
    }
  }

  @keyframes blink {
    0%,
    80%,
    100% {
      opacity: 0.25;
      transform: translateY(0);
    }
    40% {
      opacity: 1;
      transform: translateY(-2px);
    }
  }
</style>
