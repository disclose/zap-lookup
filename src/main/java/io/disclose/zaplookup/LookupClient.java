package io.disclose.zaplookup;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.parosproxy.paros.network.HttpHeader;
import org.parosproxy.paros.network.HttpMalformedHeaderException;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpRequestHeader;
import org.parosproxy.paros.network.HttpSender;

/**
 * Thin client for the lookup.disclose.io attribution API.
 *
 * <p>Privacy contract: this client sends ONLY the host string in the request
 * body — never the request line, headers, cookies, or body of the user's
 * selected HTTP message.</p>
 *
 * <p>A short-lived in-memory cache prevents lookup-spam when the same host is
 * resolved repeatedly. HTTP connect/read timeouts are governed by ZAP's
 * global Network connection options because HttpSender in ZAP 2.17.0 exposes
 * no per-request timeout.</p>
 */
public class LookupClient {

    private static final String ENDPOINT = "https://lookup.disclose.io/api/lookup";
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L; // 5 minutes
    private static final String CLIENT_ID = "zap-lookup/1.1.0";

    private final HttpSender httpSender;
    private final Gson gson;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public LookupClient() {
        this.httpSender = new HttpSender(HttpSender.MANUAL_REQUEST_INITIATOR);
        this.gson = new Gson();
    }

    /**
     * Resolves a host to its attribution + disclosure contacts.
     *
     * @param host the bare hostname (e.g. {@code github.com}) — nothing else is sent
     * @return the parsed result
     * @throws LookupException on network error, non-2xx response, or malformed JSON
     */
    public LookupResult lookup(String host) throws LookupException {
        String key = host.toLowerCase();

        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.result;
        }

        HttpMessage message = buildRequest(gson, host);

        try {
            // ZAP 2.17.0 exposes no per-request timeout here; HttpSender uses the global
            // Network connection timeout options for connect/read behavior.
            httpSender.sendAndReceive(message, true);
        } catch (SocketTimeoutException e) {
            throw new LookupException("Lookup timed out. The service may be slow or unreachable.", e);
        } catch (IOException e) {
            throw new LookupException("Could not reach lookup.disclose.io (offline?): "
                    + e.getMessage(), e);
        }

        int code = message.getResponseHeader().getStatusCode();
        if (code == 429) {
            throw new LookupException(rateLimitMessage(
                    message.getResponseHeader().getHeader("RateLimit-Limit"),
                    message.getResponseHeader().getHeader("Retry-After")));
        }
        if (code < 200 || code >= 300) {
            throw new LookupException("lookup.disclose.io returned HTTP " + code + ".");
        }

        LookupResult result = parseResponse(gson, message.getResponseBody().toString());

        cache.put(key, new CacheEntry(result));
        return result;
    }

    /** Builds the exact outbound request; exposed package-private for contract tests. */
    static HttpMessage buildRequest(Gson gson, String host) throws LookupException {
        // Only the host travels in the body. Build via Gson to guarantee valid JSON.
        String body = gson.toJson(Map.of("input", host));
        HttpMessage message;
        try {
            HttpRequestHeader requestHeader =
                    new HttpRequestHeader(
                            HttpRequestHeader.POST, new URI(ENDPOINT, true), HttpHeader.HTTP11);
            message = new HttpMessage(requestHeader);
        } catch (HttpMalformedHeaderException | URIException e) {
            throw new LookupException("Could not build the lookup request.", e);
        }

        message.getRequestHeader().setHeader(HttpHeader.CONTENT_TYPE, "application/json");
        message.getRequestHeader().setHeader("Accept", "application/json");
        message.getRequestHeader().setHeader(
                HttpHeader.USER_AGENT, CLIENT_ID + " (+https://github.com/disclose/zap-lookup)");
        message.getRequestHeader().setHeader("X-Lookup-Client", CLIENT_ID);
        message.setRequestBody(body);
        message.getRequestHeader().setContentLength(message.getRequestBody().length());
        return message;
    }

    static LookupResult parseResponse(Gson gson, String body) throws LookupException {
        LookupResult result;
        try {
            result = gson.fromJson(body, LookupResult.class);
        } catch (JsonSyntaxException e) {
            throw new LookupException("Could not parse the lookup response.", e);
        }
        if (result == null) {
            throw new LookupException("Empty response from lookup.disclose.io.");
        }
        return result;
    }

    static String rateLimitMessage(String limit, String retryAfter) {
        String displayLimit = limit == null || limit.isBlank() ? "the current quota" : limit;
        String displayRetry = retryAfter == null || retryAfter.isBlank() ? "a moment" : retryAfter + " second(s)";
        return "Rate limited by lookup.disclose.io (limit " + displayLimit + "). Retry after " + displayRetry + ".";
    }

    private static final class CacheEntry {
        final LookupResult result;
        final long storedAt;

        CacheEntry(LookupResult result) {
            this.result = result;
            this.storedAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - storedAt > CACHE_TTL_MILLIS;
        }
    }
}
