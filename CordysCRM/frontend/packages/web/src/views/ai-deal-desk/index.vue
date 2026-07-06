<template>
  <div class="deal-desk-page">
    <div class="deal-desk-shell">
      <HistoryPanel
        :sessions="sessions"
        :active-session-id="activeSessionId"
        :collapsed="historyCollapsed"
        @selectSession="activeSessionId = $event"
        @newSession="createNewSession"
        @toggleCollapse="historyCollapsed = !historyCollapsed"
      />

      <section class="conversation-panel">
        <n-scrollbar class="conversation-scroll">
          <div class="conversation-inner" :class="{ 'conversation-inner--empty': !activeSession?.turns.length }">
            <template v-if="activeSession?.turns.length">
              <ConversationTurn
                v-for="turn in activeSession.turns"
                :key="turn.id"
                :turn="turn"
                @toggleProcess="toggleProcess"
                @viewCandidate="openOpportunityDrawer"
                @viewReference="openReferenceDrawer"
                @retry="retryTurn"
              />
            </template>

            <ComposerPanel
              v-else
              :value="draftMessage"
              :references="composerReferences"
              :mention-options="mentionOptions"
              :mention-open="mentionOpen"
              :centered="true"
              :starters="starters"
              :can-send="canSend"
              :is-responding="isResponding"
              :is-stopping="isStopping"
              :placeholder="placeholder"
              @update:value="draftMessage = $event"
              @send="sendMessage"
              @stop="stopCurrentGeneration"
              @removeReference="removeReference"
              @previewReference="previewReference"
              @selectMention="selectMention"
              @openMentionPicker="openMentionPicker"
              @sendStarter="sendStarter"
              @addFiles="addFiles"
            />
          </div>
        </n-scrollbar>

        <ComposerPanel
          v-if="activeSession?.turns.length"
          :value="draftMessage"
          :references="composerReferences"
          :mention-options="mentionOptions"
          :mention-open="mentionOpen"
          :centered="false"
          :starters="[]"
          :can-send="canSend"
          :is-responding="isResponding"
          :is-stopping="isStopping"
          :placeholder="placeholder"
          @update:value="draftMessage = $event"
          @send="sendMessage"
          @stop="stopCurrentGeneration"
          @removeReference="removeReference"
          @previewReference="previewReference"
          @selectMention="selectMention"
          @openMentionPicker="openMentionPicker"
          @sendStarter="sendStarter"
          @addFiles="addFiles"
        />
      </section>
    </div>

    <OptOverviewDrawer
      v-model:show="showOpportunityOverviewDrawer"
      :detail="activeOpportunity"
      @open-customer-drawer="openCustomerDrawer"
    />
    <CustomerOverviewDrawer v-model:show="showCustomerOverviewDrawer" :source-id="activeCustomerSourceId" readonly />
    <OpenSeaOverviewDrawer
      v-model:show="showCustomerOpenseaOverviewDrawer"
      :source-id="activeCustomerSourceId"
      :pool-id="activeCustomerPoolId"
      :hidden-columns="[]"
      readonly
    />
    <n-modal v-model:show="showImagePreview" class="deal-desk-image-preview-modal">
      <section class="deal-desk-image-preview" role="dialog" :aria-label="imagePreviewTitle">
        <header class="deal-desk-image-preview__header">
          <strong>{{ imagePreviewTitle }}</strong>
          <button type="button" aria-label="关闭预览" @click="showImagePreview = false">x</button>
        </header>
        <div class="deal-desk-image-preview__body">
          <img v-if="imagePreviewUrl" :src="imagePreviewUrl" :alt="imagePreviewTitle" />
        </div>
      </section>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
  import { useRoute } from 'vue-router';
  import { NModal, NScrollbar } from 'naive-ui';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { OpportunityItem } from '@lib/shared/models/opportunity';

  import ComposerPanel from './components/ComposerPanel.vue';
  import ConversationTurn from './components/ConversationTurn.vue';
  import HistoryPanel from './components/HistoryPanel.vue';
  import CustomerOverviewDrawer from '@/views/customer/components/customerOverviewDrawer.vue';
  import OpenSeaOverviewDrawer from '@/views/customer/components/openSeaOverviewDrawer.vue';
  import OptOverviewDrawer from '@/views/opportunity/components/optOverviewDrawer.vue';

  import useAiDealDeskChat from './hooks/useAiDealDeskChat';
  import type { DealDeskReference } from './types';

  const route = useRoute();
  const { t } = useI18n();
  const {
    sessions,
    historyCollapsed,
    mentionOpen,
    draftMessage,
    composerReferences,
    activeSessionId,
    activeSession,
    isResponding,
    isStopping,
    starters,
    mentionOptions,
    createNewSession,
    toggleProcess,
    removeReference,
    addFiles,
    selectMention,
    openMentionPicker,
    sendStarter,
    sendMessage,
    retryTurn,
    stopCurrentGeneration,
  } = useAiDealDeskChat(route);

  const placeholder = computed(() =>
    activeSession.value?.turns.length ? t('aiDealDesk.inputPlaceholderWithContext') : t('aiDealDesk.inputPlaceholder')
  );
  const canSend = computed(
    () => !isResponding.value && (draftMessage.value.trim().length > 0 || composerReferences.value.length > 0)
  );

  const showOpportunityOverviewDrawer = ref(false);
  const activeOpportunity = ref<Partial<OpportunityItem>>();
  const showCustomerOverviewDrawer = ref(false);
  const showCustomerOpenseaOverviewDrawer = ref(false);
  const activeCustomerSourceId = ref('');
  const activeCustomerPoolId = ref('');
  const showImagePreview = ref(false);
  const imagePreviewUrl = ref('');
  const imagePreviewTitle = ref('');

  function openOpportunityDrawer(source: DealDeskReference) {
    activeOpportunity.value = {
      id: source.id,
      name: source.label.replace(/^@/, ''),
      opportunityName: source.label.replace(/^@/, ''),
    };
    showOpportunityOverviewDrawer.value = true;
  }

  function openCustomerDrawer(params: { customerId: string; inCustomerPool?: boolean; poolId?: string }) {
    activeCustomerSourceId.value = params.customerId;
    activeCustomerPoolId.value = params.poolId || '';
    if (params.inCustomerPool) {
      showCustomerOpenseaOverviewDrawer.value = true;
      return;
    }
    showCustomerOverviewDrawer.value = true;
  }

  function previewReference(reference: DealDeskReference) {
    if (reference.type !== 'file' || !reference.url) {
      return;
    }
    imagePreviewUrl.value = reference.url;
    imagePreviewTitle.value = reference.label.replace(/^@/, '');
    showImagePreview.value = true;
  }

  function openReferenceDrawer(reference: DealDeskReference) {
    if (reference.type === 'file' && reference.url) {
      previewReference(reference);
      return;
    }
    if (reference.type === 'customer') {
      openCustomerDrawer({ customerId: reference.id });
      return;
    }
    openOpportunityDrawer(reference);
  }
</script>

<style scoped lang="less">
  .deal-desk-page {
    height: 100%;
  }

  .deal-desk-shell {
    display: flex;
    height: calc(100vh - 88px);
    min-height: 680px;
    overflow: hidden;
    border: 1px solid #dde6ee;
    border-radius: 0;
    background: radial-gradient(circle at 50% 16%, rgb(218 245 249 / 42%) 0%, rgb(248 251 253 / 0%) 26%),
      linear-gradient(180deg, #fbfdff 0%, #f7f9fb 100%);
  }

  .conversation-panel {
    display: flex;
    min-width: 0;
    flex: 1;
    flex-direction: column;
    padding: 0 40px 24px;
  }

  .conversation-scroll {
    flex: 1;
    min-height: 0;
  }

  .conversation-inner {
    min-height: 100%;
    width: min(1180px, 100%);
    margin: 0 auto;
    padding: 28px 0 0;
    &--empty {
      display: flex;
      flex-direction: column;
    }
  }

  :deep(.deal-desk-image-preview-modal) {
    width: auto;
  }

  .deal-desk-image-preview {
    overflow: hidden;
    width: min(860px, calc(100vw - 48px));
    max-height: calc(100vh - 64px);
    border-radius: 8px;
    background: #fff;
    box-shadow: 0 22px 60px rgb(15 23 42 / 22%);
  }

  .deal-desk-image-preview__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    min-height: 52px;
    padding: 0 18px;
    border-bottom: 1px solid #edf2f5;
    color: var(--text-n1);

    strong {
      min-width: 0;
      overflow: hidden;
      font-size: 15px;
      font-weight: 600;
      line-height: 22px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    button {
      flex: none;
      width: 28px;
      height: 28px;
      border: 0;
      border-radius: 50%;
      color: var(--text-n4);
      background: transparent;
      cursor: pointer;

      &:hover {
        color: var(--text-n1);
        background: #f1f5f7;
      }
    }
  }

  .deal-desk-image-preview__body {
    display: flex;
    align-items: center;
    justify-content: center;
    max-height: calc(100vh - 116px);
    min-height: 240px;
    padding: 16px;
    background: #f7fafb;
    overflow: auto;

    img {
      display: block;
      max-width: 100%;
      max-height: calc(100vh - 156px);
      border-radius: 8px;
      object-fit: contain;
      background: #fff;
      box-shadow: 0 10px 30px rgb(15 23 42 / 12%);
    }
  }
</style>
