<template>
  <div class="composer-shell" :class="{ 'composer-shell--centered': centered }">
    <div v-if="centered" class="composer-empty-copy">
      <div class="composer-empty-hero" aria-hidden="true">
        <span class="composer-empty-hero__spark composer-empty-hero__spark--left">+</span>
        <span class="composer-empty-hero__bubble">
          <span></span>
          <span></span>
          <span></span>
        </span>
        <span class="composer-empty-hero__spark composer-empty-hero__spark--right">+</span>
      </div>
      <h1>{{ t('aiDealDesk.emptyTitle') }}</h1>
      <p>{{ t('aiDealDesk.emptyDesc') }}</p>
    </div>

    <div ref="composerRef" class="composer" @paste="handlePaste">
      <div v-if="attachmentReferences.length" class="composer-image-list">
        <div
          v-for="reference in attachmentReferences"
          :key="reference.id"
          class="composer-image-chip"
          role="button"
          tabindex="0"
          :title="reference.label"
          @click="previewReference(reference)"
          @keydown.enter.prevent="previewReference(reference)"
          @keydown.space.prevent="previewReference(reference)"
        >
          <img v-if="reference.url" :src="reference.url" :alt="reference.label" />
          <span>{{ reference.label.replace(/^@/, '') }}</span>
          <button
            type="button"
            class="composer-image-chip__remove"
            :aria-label="t('aiDealDesk.removeImage')"
            @click.stop="$emit('removeReference', reference.id)"
          >
            x
          </button>
        </div>
      </div>
      <div class="composer__inner">
        <div class="composer__tools">
          <n-tooltip trigger="hover" :delay="300">
            <template #trigger>
              <n-button quaternary circle :title="t('aiDealDesk.uploadImage')" @click="openFilePicker">
                <template #icon>
                  <ImageOutline />
                </template>
              </n-button>
            </template>
            {{ t('aiDealDesk.uploadImageTip') }}
          </n-tooltip>
          <input
            ref="fileInputRef"
            class="hidden"
            type="file"
            multiple
            accept="image/png,image/jpeg,image/gif,image/webp,image/svg+xml"
            @change="handleFileChange"
          />
        </div>
        <div v-if="inlineReferences.length" class="composer-inline-references">
          <button
            v-for="reference in inlineReferences"
            :key="reference.id"
            type="button"
            class="composer-reference-pill"
            :class="`composer-reference-pill--${reference.type}`"
            @click="$emit('removeReference', reference.id)"
          >
            <span>{{ reference.label }}</span>
            <span class="composer-reference-pill__close">x</span>
          </button>
        </div>
        <n-input
          :value="value"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: centered ? 5 : 4 }"
          :placeholder="placeholder"
          class="composer__textarea"
          @update:value="handleInput"
          @keydown.enter.exact.prevent="emitSend"
        />
        <n-button
          type="primary"
          circle
          :class="{ 'composer__send--stop': isResponding, 'composer__send--stopping': isStopping }"
          :disabled="!isResponding && !canSend"
          :aria-label="isResponding ? '停止生成' : '发送'"
          @click="handlePrimaryAction"
        >
          <template #icon>
            <StopCircleOutline v-if="isResponding" />
            <PaperPlaneOutline v-else />
          </template>
        </n-button>
      </div>
      <div v-if="mentionOpen && mentionOptions.length" class="mention-picker">
        <button
          v-for="option in mentionOptions"
          :key="option.id"
          type="button"
          class="mention-option"
          @click="handleSelectMention(option)"
        >
          <span class="mention-option__content">
            <span class="mention-option__label">{{ option.label }}</span>
            <span v-if="option.subtitle" class="mention-option__meta">{{ option.subtitle }}</span>
          </span>
        </button>
      </div>
    </div>

    <div v-if="centered && starters.length" class="starter-list">
      <button
        v-for="starter in starters"
        :key="starter"
        type="button"
        class="starter-item"
        @click="$emit('sendStarter', starter)"
      >
        {{ starter }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { NButton, NInput, NTooltip } from 'naive-ui';
  import { ImageOutline, PaperPlaneOutline, StopCircleOutline } from '@vicons/ionicons5';

  import { useI18n } from '@lib/shared/hooks/useI18n';

  import type { DealDeskReference } from '../types';

  const props = defineProps<{
    value: string;
    references: DealDeskReference[];
    mentionOptions: Array<DealDeskReference & { subtitle?: string }>;
    mentionOpen: boolean;
    centered: boolean;
    starters: string[];
    canSend: boolean;
    isResponding?: boolean;
    isStopping?: boolean;
    placeholder: string;
  }>();

  const emit = defineEmits<{
    (e: 'update:value', value: string): void;
    (e: 'send'): void;
    (e: 'removeReference', referenceId: string): void;
    (e: 'selectMention', reference: DealDeskReference & { subtitle?: string }): void;
    (e: 'openMentionPicker'): void;
    (e: 'sendStarter', text: string): void;
    (e: 'addFiles', files: File[]): void;
    (e: 'previewReference', reference: DealDeskReference): void;
    (e: 'stop'): void;
  }>();

  const { t } = useI18n();
  const composerRef = ref<HTMLElement | null>(null);
  const fileInputRef = ref<HTMLInputElement | null>(null);
  const inlineReferences = computed(() => props.references.filter((reference) => reference.type !== 'file'));
  const attachmentReferences = computed(() => props.references.filter((reference) => reference.type === 'file'));

  function emitSend() {
    if (!props.isResponding && props.canSend) {
      emit('send');
    }
  }

  function handlePrimaryAction() {
    if (props.isResponding) {
      emit('stop');
      return;
    }
    emitSend();
  }

  function handleInput(value: string) {
    emit('update:value', value);
    if (/@[^\s@]*$/.test(value)) {
      emit('openMentionPicker');
    }
  }

  function openFilePicker() {
    fileInputRef.value?.click();
  }

  function focusTextarea() {
    nextTick(() => {
      const textarea = composerRef.value?.querySelector('textarea');
      if (!(textarea instanceof HTMLTextAreaElement)) {
        return;
      }
      textarea.focus();
      const caret = textarea.value.length;
      textarea.setSelectionRange(caret, caret);
    });
  }

  function handleSelectMention(reference: DealDeskReference & { subtitle?: string }) {
    emit('selectMention', reference);
    focusTextarea();
  }

  function handleFileChange(event: Event) {
    const target = event.target as HTMLInputElement;
    const files = Array.from(target.files ?? []);
    if (files.length) {
      emit('addFiles', files);
    }
    target.value = '';
  }

  function previewReference(reference: DealDeskReference) {
    emit('previewReference', reference);
  }

  function buildPastedImageName(file: File, index: number) {
    const extension = file.type.split('/')[1]?.replace('jpeg', 'jpg') || 'png';
    return `pasted-image-${Date.now()}-${index + 1}.${extension}`;
  }

  function normalizePastedImage(file: File, index: number) {
    if (file.name) {
      return file;
    }
    return new File([file], buildPastedImageName(file, index), {
      type: file.type || 'image/png',
      lastModified: Date.now(),
    });
  }

  function getClipboardImageFiles(event: ClipboardEvent) {
    const { clipboardData } = event;
    if (!clipboardData) {
      return [];
    }

    const itemFiles = Array.from(clipboardData.items ?? [])
      .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
      .map((item) => item.getAsFile())
      .filter((file): file is File => Boolean(file));

    const files = itemFiles.length
      ? itemFiles
      : Array.from(clipboardData.files ?? []).filter((file) => file.type.startsWith('image/'));

    return files.map(normalizePastedImage);
  }

  function handlePaste(event: ClipboardEvent) {
    const imageFiles = getClipboardImageFiles(event);
    if (!imageFiles.length) {
      return;
    }
    event.preventDefault();
    emit('addFiles', imageFiles);
  }
</script>

<style scoped lang="less">
  .composer-shell {
    position: sticky;
    bottom: 0;
    padding: 18px 0 0;
    background: linear-gradient(
      180deg,
      rgb(249 251 251 / 0%) 0%,
      rgb(249 251 251 / 92%) 24%,
      rgb(249 251 251 / 100%) 100%
    );
    &--centered {
      position: static;
      display: flex;
      flex: 1;
      flex-direction: column;
      align-items: center;
      justify-content: flex-start;
      padding: 108px 0 0;
      background: transparent;
    }
  }

  .composer-empty-copy {
    margin-bottom: 34px;
    text-align: center;

    .composer-empty-hero {
      position: relative;
      width: 230px;
      height: 118px;
      margin: 0 auto 14px;
      border-top: 1px solid rgb(210 230 235 / 72%);
      border-radius: 50% 50% 0 0 / 100% 100% 0 0;
    }

    .composer-empty-hero__bubble {
      position: absolute;
      left: 50%;
      top: 44px;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      width: 66px;
      height: 46px;
      border-radius: 13px;
      background: linear-gradient(135deg, #11a8ca 0%, #8ed8de 100%);
      box-shadow: 0 16px 34px rgb(42 164 186 / 22%);
      transform: translateX(-50%);

      &::after {
        position: absolute;
        bottom: -9px;
        left: 10px;
        width: 14px;
        height: 14px;
        background: #24b5ce;
        clip-path: polygon(0 0, 100% 0, 0 100%);
        content: '';
      }

      span {
        width: 7px;
        height: 7px;
        border-radius: 50%;
        background: rgb(255 255 255 / 88%);
      }
    }

    .composer-empty-hero__spark {
      position: absolute;
      color: #a9dce5;
      font-size: 18px;
      font-weight: 600;
      line-height: 1;
    }

    .composer-empty-hero__spark--left {
      left: 66px;
      top: 46px;
    }

    .composer-empty-hero__spark--right {
      right: 64px;
      top: 26px;
    }

    h1 {
      margin: 0 0 12px;
      font-size: 26px;
      font-weight: 600;
      color: var(--text-n1);
      line-height: 36px;
    }

    p {
      margin: 0;
      font-size: 16px;
      color: var(--text-n4);
      line-height: 24px;
    }
  }

  .composer {
    position: relative;
    width: 100%;
    max-width: 960px;
    margin: 0 auto;
    padding: 8px 12px 8px 14px;
    border: 1px solid #d6e2e8;
    border-radius: 8px;
    background: var(--text-n10);
    box-shadow: 0 12px 30px rgb(17 38 66 / 8%);
  }

  .composer-image-list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 10px;
  }

  .composer-image-chip {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    cursor: pointer;
    padding: 3px 8px 3px 4px;
    max-width: 220px;
    height: 34px;
    font-size: 12px;
    line-height: 18px;
    border: 1px solid #cfe2e5;
    border-radius: 8px;
    color: var(--text-n2);
    background: #f7fbfc;
    img {
      width: 26px;
      height: 26px;
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
    .composer-image-chip__remove {
      flex: none;
      padding: 0 2px;
      border: 0;
      background: transparent;
      font-size: 11px;
      color: var(--text-n4);
      line-height: 16px;
      cursor: pointer;
      &:hover {
        color: #ef4444;
      }
    }
    &:hover {
      border-color: #8ccdd8;
      background: #f0fafb;
    }
  }

  .composer__inner {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  .composer__tools {
    display: flex;
    gap: 4px;
  }

  .composer-shell--centered .composer__tools {
    :deep(.n-button) {
      color: #263b59;
    }
  }

  .composer__textarea {
    flex: 1;
    min-width: 280px;
  }

  .composer__textarea {
    :deep(textarea) {
      font-size: 16px;
      line-height: 24px;
    }

    :deep(.n-input) {
      background: transparent;
    }

    :deep(.n-input-wrapper) {
      min-height: 44px;
      padding-top: 8px;
      padding-bottom: 8px;
      padding-left: 2px;
      padding-right: 2px;
      align-items: center;
    }

    :deep(.n-input__border),
    :deep(.n-input__state-border) {
      display: none;
    }

    :deep(.n-input__placeholder) {
      color: #8da0b8;
      font-size: 16px;
    }
  }

  .composer-inline-references {
    display: flex;
    flex: none;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    max-width: 100%;
    padding-right: 2px;
  }

  .composer-reference-pill {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    max-width: 100%;
    padding: 2px 8px;
    border: none;
    border-radius: 10px;
    background: rgb(39 184 209 / 10%);
    color: #2295a7;
    font-size: 13px;
    line-height: 22px;
    white-space: nowrap;
    span:first-child {
      overflow: hidden;
      text-overflow: ellipsis;
    }
    &--opportunity {
      background: rgb(80 126 255 / 10%);
      color: #4669c7;
    }
  }

  .composer-reference-pill__close {
    flex: none;
    font-size: 11px;
    color: currentColor;
    opacity: 0.78;
  }

  .composer-shell--centered .composer__textarea {
    :deep(.n-input-wrapper) {
      min-height: 48px;
      padding-top: 10px;
      padding-bottom: 10px;
    }
  }

  .composer-shell--centered :deep(.n-button--primary-type) {
    width: 42px;
    height: 42px;
    background: linear-gradient(135deg, #27b8d1 0%, #8ddbe1 100%);
    box-shadow: 0 8px 18px rgb(39 184 209 / 28%);
  }

  .composer__send--stop {
    background: #ef4444 !important;
    box-shadow: 0 8px 18px rgb(239 68 68 / 24%);

    :deep(.n-button__icon) {
      color: #fff;
    }
  }

  .composer__send--stopping {
    :deep(.n-button__icon) {
      opacity: 0.72;
    }
  }

  .mention-picker {
    position: absolute;
    bottom: calc(100% + 8px);
    left: 12px;
    width: 420px;
    max-width: calc(100% - 24px);
    padding: 6px;
    border: 1px solid #d7e4e7;
    border-radius: var(--border-radius-medium);
    background: var(--text-n10);
    box-shadow: 0 12px 30px rgb(15 23 42 / 9%);
  }

  .mention-option {
    display: block;
    width: 100%;
    padding: 8px 10px;
    border-radius: var(--border-radius-small);
    text-align: left;
    &:hover {
      background: var(--primary-7);
    }
  }

  .mention-option__content {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
  }

  .mention-option__label {
    min-width: 0;
    overflow: hidden;
    color: var(--text-n1);
    line-height: 20px;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .mention-option__meta {
    flex: none;
    max-width: 42%;
    overflow: hidden;
    font-size: 12px;
    color: var(--text-n1);
    line-height: 18px;
    white-space: nowrap;
    text-overflow: ellipsis;
    color: var(--text-n4);
  }

  .starter-list {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 12px 16px;
    width: 100%;
    max-width: 980px;
    margin-top: 44px;
  }

  .starter-item {
    min-width: 194px;
    padding: 10px 22px;
    border: 1px solid #d9e1ea;
    border-radius: 8px;
    background: var(--text-n10);
    color: var(--text-n2);
    font-size: 16px;
    line-height: 24px;
    box-shadow: 0 4px 14px rgb(15 23 42 / 3%);
    &:hover {
      border-color: #8ccdd8;
      color: var(--text-n1);
      background: #f3fafb;
    }
  }
</style>
