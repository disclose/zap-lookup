package io.disclose.zaplookup;

import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LookupResultTest {

    private static final String ROUTE_AWARE = """
        {"input":"cloudflare.com","assetType":"domain","status":"complete",
         "attribution":{"organization":"Cloudflare"},
         "contacts":[
           {"type":"convention","value":"security@cloudflare.com","verified":false},
           {"type":"bug_bounty","value":"https://owner.example","verified":true}
         ],
         "contactGroups":[
           {"entity":"Cloudflare","relation":"self","routeClass":"first_party","contacts":[{"type":"bug_bounty","value":"https://owner.example","deliveryAgent":"HackerOne"}]},
           {"entity":"CERT/CC","relation":"coordinator","routeClass":"coordinator","contacts":[{"type":"cert","value":"https://cert.example"}]}
         ],
         "routeSummary":{"routeClass":"first_party","headline":"First-party reporting route found","firstPartyFound":true,"ownerRouteFound":true,"coordinatorAvailable":true}}
        """;

    @Test
    void preservesFlatAndGroupOrderFromServer() {
        LookupResult result = new Gson().fromJson(ROUTE_AWARE, LookupResult.class);
        assertEquals(List.of("convention", "bug_bounty"),
                result.contactsInServerOrder().stream().map(LookupResult.Contact::type).toList());
        assertEquals(List.of("Cloudflare", "CERT/CC"),
                result.contactGroups().stream().map(LookupResult.ContactGroup::entity).toList());
        assertEquals("HackerOne", result.contactGroups().get(0).contacts().get(0).deliveryAgent());
        assertEquals("First-party reporting route found", result.routeSummary().headline());
    }

    @Test
    void emptyResponseIsNullSafe() {
        LookupResult result = new Gson().fromJson("{}", LookupResult.class);
        assertTrue(result.contactsInServerOrder().isEmpty());
        assertTrue(result.contactGroups().isEmpty());
        assertNull(result.routeSummary());
    }
}
