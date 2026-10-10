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
import org.zaproxy.zap.network.HttpRequestConfig;

/**
 * Thin client for the lookup.disclose.io attribution API.
 *
 * <p>Privacy contract: this client sends ONLY the host string in the request
 * body — never the request line, headers, cookies, or body of the user's
 * selected HTTP message.</p>
 *
 * <p>A short-lived in-memory cache prevents lookup-spam when the same host is
 * resolved repeatedly. The response timeout is set per request (ZAP's global
 * default is 20s, shorter than a cold lookup for a large organization).</p>
 */
public class LookupClient {

    private static final String ENDPOINT = "https://lookup.disclose.io/api/lookup";
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L; // 5 minutes
    private static final String CLIENT_ID = "zap-lookup/1.1.1";
    // A first-time (uncached) lookup for a large organization takes 30 to 40s
    // server-side, so the response deadline must sit comfortably above that.
    static final int RESPONSE_TIMEOUT_MILLIS = 60_000;
    // The service sheds load with 503 + Retry-After; retry once after the
    // advertised delay, capped so the UI never waits unreasonably.
    private static final long BUSY_RETRY_MAX_WAIT_SECONDS = 15;
    static final HttpRequestConfig REQUEST_CONFIG = HttpRequestConfig.builder()
            .setFollowRedirects(true)
            .setSoTimeout(RESPONSE_TIMEOUT_MILLIS)
            .build();

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
            httpSender.sendAndReceive(message, REQUEST_CONFIG);
            if (message.getResponseHeader().getStatusCode() == 503) {
                Thread.sleep(busyRetryWaitSeconds(message.getResponseHeader().getHeader("Retry-After")) * 1000L);
                message = buildRequest(gson, host);
                httpSender.sendAndReceive(message, REQUEST_CONFIG);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LookupException("Lookup was interrupted.", e);
        } catch (SocketTimeoutException e) {
            throw new LookupException("Lookup timed out after " + (RESPONSE_TIMEOUT_MILLIS / 1000)
                    + "s. First-time lookups for large organizations can be slow; try again.", e);
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
        if (code == 503) {
            throw new LookupException(busyMessage(message.getResponseHeader().getHeader("Retry-After")));
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

    static long busyRetryWaitSeconds(String retryAfter) {
        try {
            long advertised = Long.parseLong(retryAfter == null ? "" : retryAfter.trim());
            if (advertised > 0) return Math.min(advertised, BUSY_RETRY_MAX_WAIT_SECONDS);
        } catch (NumberFormatException ignored) {
            // fall through to the default
        }
        return 5;
    }

    static String busyMessage(String retryAfter) {
        return "lookup.disclose.io is busy. Try again in "
                + (retryAfter == null || retryAfter.isBlank() ? "a few" : retryAfter.trim())
                + " second(s).";
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
