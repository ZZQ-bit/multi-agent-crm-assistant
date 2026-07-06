import type { DealDeskMentionOption, DealDeskReference } from '../types';

function normalizeMentionKeyword(value: string) {
  return value.trim().replace(/^@/, '').toLowerCase().replace(/\s+/g, '');
}

function getQueryValue(value: unknown) {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : null;
  }

  return typeof value === 'string' ? value : null;
}

export function matchesMentionKeyword(values: string[], keyword: string) {
  if (!keyword) {
    return true;
  }

  return values.some((value) => normalizeMentionKeyword(value).includes(keyword));
}

export function buildCustomerMentionOptionsFromList(
  customers: Array<{ id: string; name: string; ownerName?: string; departmentName?: string }>,
  keyword: string
): DealDeskMentionOption[] {
  const normalizedKeyword = normalizeMentionKeyword(keyword);
  return customers
    .filter((customer) =>
      matchesMentionKeyword([customer.name, customer.ownerName || '', customer.departmentName || ''], normalizedKeyword)
    )
    .map((customer) => ({
      id: customer.id,
      label: `@${customer.name}`,
      type: 'customer' as const,
      source: 'mention' as const,
      subtitle: customer.ownerName ? `客户 / ${customer.ownerName}` : '客户',
    }));
}

export function buildOpportunityMentionOptionsFromList(
  opportunities: Array<{
    id: string;
    name?: string;
    opportunityName?: string;
    customerName?: string;
    stageName?: string;
    ownerName?: string;
  }>,
  keyword: string
): DealDeskMentionOption[] {
  const normalizedKeyword = normalizeMentionKeyword(keyword);
  return opportunities
    .filter((opportunity) =>
      matchesMentionKeyword(
        [
          opportunity.opportunityName || opportunity.name || '',
          opportunity.customerName || '',
          opportunity.stageName || '',
          opportunity.ownerName || '',
        ],
        normalizedKeyword
      )
    )
    .map((opportunity) => ({
      id: opportunity.id,
      label: `@${opportunity.opportunityName || opportunity.name || ''}`,
      type: 'opportunity' as const,
      source: 'mention' as const,
      subtitle: opportunity.stageName ? `商机 / ${opportunity.stageName}` : '商机',
    }));
}

export function getRouteReference(query: Record<string, unknown>): DealDeskReference | null {
  const opportunityId = getQueryValue(query.opportunityId);
  const opportunityName = getQueryValue(query.opportunityName);
  const customerId = getQueryValue(query.customerId);
  const customerName = getQueryValue(query.customerName);

  if (opportunityId) {
    return {
      id: opportunityId,
      label: `@${opportunityName || opportunityId}`,
      type: 'opportunity',
      source: 'route_query',
    };
  }

  if (customerId) {
    return {
      id: customerId,
      label: `@${customerName || customerId}`,
      type: 'customer',
      source: 'route_query',
    };
  }

  return null;
}

export function resolveBoundObject(references: DealDeskReference[]) {
  return references.find((item) => item.type === 'customer' || item.type === 'opportunity') ?? null;
}
