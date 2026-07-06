<template>
  <CrmTag v-if="props.status !== CustomerFollowPlanStatusEnum.ALL" :type="currentStatus.type" theme="light">
    <template #icon>
      <CrmIcon :type="currentStatus.icon" :size="16" :class="`text-[${currentStatus.color}]`" />
    </template>
    <div :class="`text-[${currentStatus.color}] flex items-center gap-[8px]`">
      {{ t(currentStatus.label) }}
      <CrmIcon v-if="!props.hiddenDownIcon" type="iconicon_chevron_down" :size="16" class="text-[var(--text-n4)]" />
    </div>
  </CrmTag>
</template>

<script lang="ts" setup>
  import { CustomerFollowPlanStatusEnum } from '@lib/shared/enums/customerEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmTag from '@/components/pure/crm-tag/index.vue';

  import { statusMap } from '@/config/follow';

  const { t } = useI18n();

  const props = defineProps<{
    status: CustomerFollowPlanStatusEnum | string;
    hiddenDownIcon?: boolean;
  }>();

  const currentStatus = computed(() => statusMap[props.status as keyof typeof statusMap] ?? statusMap.PREPARED);
</script>

<style lang="less" scoped></style>
