package io.disclose.zaplookup;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.parosproxy.paros.network.HttpMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LookupClientTest {

    @Test
    void exactRequestIdentifiesTheClientAndSendsOnlyTheHost() throws Exception {
        HttpMessage message = LookupClient.buildRequest(new Gson(), "github.com");
        assertEquals("zap-lookup/1.1.1 (+https://github.com/disclose/zap-lookup)",
                message.getRequestHeader().getHeader("User-Agent"));
        assertEquals("zap-lookup/1.1.1", message.getRequestHeader().getHeader("X-Lookup-Client"));
        assertEquals(null, message.getRequestHeader().getHeader("Authorization"));
        JsonObject body = new Gson().fromJson(message.getRequestBody().toString(), JsonObject.class);
        assertEquals(1, body.size());
        assertEquals("github.com", body.get("input").getAsString());
    }

    @Test
    void parsesAValidResponseAndRejectsMalformedJson() throws Exception {
        LookupResult result = LookupClient.parseResponse(
                new Gson(), "{\"status\":\"complete\",\"contacts\":[]}");
        assertEquals("complete", result.status());
        assertThrows(LookupException.class,
                () -> LookupClient.parseResponse(new Gson(), "{\"status\":"));
    }

    @Test
    void requestsUseA60sResponseTimeoutInsteadOfZapsGlobal20s() {
        assertEquals(60_000, LookupClient.REQUEST_CONFIG.getSoTimeout());
        assertTrue(LookupClient.REQUEST_CONFIG.isFollowRedirects());
    }

    @Test
    void busyResponsesAreRetriedAfterACappedDelayAndReportedClearly() {
        assertEquals(7, LookupClient.busyRetryWaitSeconds("7"));
        assertEquals(15, LookupClient.busyRetryWaitSeconds("120"));
        assertEquals(5, LookupClient.busyRetryWaitSeconds(null));
        assertTrue(LookupClient.busyMessage("15").contains("busy"));
        assertTrue(LookupClient.busyMessage("15").contains("15 second"));
    }

    @Test
    void rateLimitMessageUsesServerPolicyInsteadOfAHardcodedQuota() {
        String message = LookupClient.rateLimitMessage("600", "7");
        assertTrue(message.contains("600"));
        assertTrue(message.contains("7 second"));
        assertTrue(LookupClient.rateLimitMessage(null, null).contains("current quota"));
    }
}
