package io.disclose.zaplookup;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LookupPopupMenuItemTest {

    @Test
    void rendersRouteGroupsInServerOrder() {
        LookupResult result = new Gson().fromJson("""
            {"status":"complete","attribution":{"organization":"Cloudflare"},
             "contactGroups":[
               {"entity":"Owner","relation":"self","routeClass":"first_party","contacts":[{"type":"email","value":"security@owner.example"}]},
               {"entity":"CERT/CC","relation":"coordinator","routeClass":"coordinator","contacts":[{"type":"cert","value":"https://cert.example"}]}
             ],
             "routeSummary":{"routeClass":"first_party","headline":"First-party route found"}}
            """, LookupResult.class);
        String rendered = LookupPopupMenuItem.formatResult("cloudflare.com", result);
        assertTrue(rendered.indexOf("Owner") < rendered.indexOf("CERT/CC"));
        assertTrue(rendered.contains("first_party / self"));
        assertTrue(rendered.contains("First-party route found"));
    }
}
