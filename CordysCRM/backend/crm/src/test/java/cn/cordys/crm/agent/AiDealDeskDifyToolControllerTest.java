package cn.cordys.crm.agent;

import cn.cordys.crm.ai.dealdesk.controller.AiDealDeskDifyToolController;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolResponse;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskToolExecutionContext;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskToolService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiDealDeskDifyToolControllerTest {

    @Test
    void shouldRejectRequestsWithInvalidToken() {
        FakeToolService service = new FakeToolService();
        AiDealDeskDifyToolController controller = new AiDealDeskDifyToolController(service, "secret-token", "admin", "100001");

        DealDeskToolResponse response = controller.invokeTool("search-customers", "wrong-token", new DealDeskToolRequest());

        assertFalse(response.isSuccess());
        assertEquals("UNAUTHORIZED", response.getCode());
        assertEquals(0, service.searchCustomersCalls);
    }

    @Test
    void shouldRejectRequestsWhenServerTokenIsNotConfigured() {
        FakeToolService service = new FakeToolService();
        AiDealDeskDifyToolController controller = new AiDealDeskDifyToolController(service, "", "admin", "100001");

        DealDeskToolResponse response = controller.invokeTool("search-customers", "secret-token", new DealDeskToolRequest());

        assertFalse(response.isSuccess());
        assertEquals("UNAUTHORIZED", response.getCode());
        assertEquals(0, service.searchCustomersCalls);
    }

    @Test
    void shouldDispatchKnownToolWithValidToken() {
        FakeToolService service = new FakeToolService();
        AiDealDeskDifyToolController controller = new AiDealDeskDifyToolController(service, "secret-token", "admin", "100001");
        DealDeskToolRequest request = new DealDeskToolRequest();
        request.setKeyword("huadong");

        DealDeskToolResponse response = controller.invokeTool("search-customers", "secret-token", request);

        assertTrue(response.isSuccess());
        assertEquals("search-customers", response.getData().get("tool"));
        assertEquals(1, service.searchCustomersCalls);
    }

    @Test
    void shouldDispatchCrmObjectResolverWithValidToken() {
        FakeToolService service = new FakeToolService();
        AiDealDeskDifyToolController controller = new AiDealDeskDifyToolController(service, "secret-token", "admin", "100001");
        DealDeskToolRequest request = new DealDeskToolRequest();
        request.setObjectReference("华东智造集团AI客服升级项目");

        DealDeskToolResponse response = controller.invokeTool("resolve-crm-object", "secret-token", request);

        assertTrue(response.isSuccess());
        assertEquals("resolve-crm-object", response.getData().get("tool"));
        assertEquals(1, service.resolveCrmObjectCalls);
    }

    @Test
    void shouldRejectUnknownToolName() {
        FakeToolService service = new FakeToolService();
        AiDealDeskDifyToolController controller = new AiDealDeskDifyToolController(service, "secret-token", "admin", "100001");

        DealDeskToolResponse response = controller.invokeTool("update-opportunity", "secret-token", new DealDeskToolRequest());

        assertFalse(response.isSuccess());
        assertEquals("TOOL_NOT_FOUND", response.getCode());
    }

    @Test
    void shouldRunToolWithConfiguredCrmIdentityAndClearItAfterwards() {
        FakeToolService service = new FakeToolService();
        AiDealDeskDifyToolController controller = new AiDealDeskDifyToolController(service, "secret-token", "admin", "100001");

        DealDeskToolResponse response = controller.invokeTool("get-opportunity-context", "secret-token", new DealDeskToolRequest());

        assertTrue(response.isSuccess());
        assertEquals("admin", response.getData().get("userId"));
        assertEquals("100001", response.getData().get("organizationId"));
        assertEquals(1, service.getOpportunityContextCalls);
        assertFalse(AiDealDeskToolExecutionContext.hasBoundUserId());
    }

    private static class FakeToolService extends AiDealDeskToolService {
        private int searchCustomersCalls;
        private int resolveCrmObjectCalls;
        private int getOpportunityContextCalls;

        @Override
        public DealDeskToolResponse searchCustomers(DealDeskToolRequest request) {
            searchCustomersCalls += 1;
            return DealDeskToolResponse.ok(Map.of("tool", "search-customers"));
        }

        @Override
        public DealDeskToolResponse resolveCrmObject(DealDeskToolRequest request) {
            resolveCrmObjectCalls += 1;
            return DealDeskToolResponse.ok(Map.of("tool", "resolve-crm-object"));
        }

        @Override
        public DealDeskToolResponse getOpportunityContext(DealDeskToolRequest request) {
            getOpportunityContextCalls += 1;
            return DealDeskToolResponse.ok(Map.of(
                    "userId", AiDealDeskToolExecutionContext.getUserId(),
                    "organizationId", AiDealDeskToolExecutionContext.getOrganizationId()
            ));
        }
    }
}
