package cn.cordys.crm.ai.dealdesk.service;

import cn.cordys.context.OrganizationContext;
import cn.cordys.security.SessionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.function.Supplier;

public final class AiDealDeskToolExecutionContext {
    private static final ThreadLocal<String> USER_ID = new InheritableThreadLocal<>();
    private static final ThreadLocal<String> ORGANIZATION_ID = new InheritableThreadLocal<>();

    private AiDealDeskToolExecutionContext() {
    }

    public static <T> T runAs(String userId, String organizationId, Supplier<T> supplier) {
        if (StringUtils.isNotBlank(userId)) {
            USER_ID.set(userId);
        }
        if (StringUtils.isNotBlank(organizationId)) {
            ORGANIZATION_ID.set(organizationId);
            OrganizationContext.setOrganizationId(organizationId);
        }
        try {
            return supplier.get();
        } finally {
            USER_ID.remove();
            ORGANIZATION_ID.remove();
            OrganizationContext.clear();
        }
    }

    public static String getUserId() {
        String userId = USER_ID.get();
        if (StringUtils.isNotBlank(userId)) {
            return userId;
        }
        return StringUtils.defaultString(SessionUtils.getUserId());
    }

    public static String getOrganizationId() {
        String organizationId = ORGANIZATION_ID.get();
        if (StringUtils.isNotBlank(organizationId)) {
            return organizationId;
        }
        return StringUtils.defaultString(OrganizationContext.getOrganizationId());
    }

    public static boolean hasBoundUserId() {
        return StringUtils.isNotBlank(USER_ID.get());
    }
}
