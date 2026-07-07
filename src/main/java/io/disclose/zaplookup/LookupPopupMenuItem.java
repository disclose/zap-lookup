package io.disclose.zaplookup;

import org.apache.commons.httpclient.URIException;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.view.View;
import org.zaproxy.zap.view.popup.PopupMenuItemHttpMessageContainer;

import javax.swing.SwingUtilities;
import java.util.List;

/**
 * "Find disclosure contact" right-click menu item.
 *
 * <p>Sends ONLY the selected message's host to lookup.disclose.io and shows the
 * attribution + ranked disclosure contacts in a plain message dialog. The
 * blocking HTTP call runs on a background daemon thread so the UI never freezes;
 * the result is rendered back on the Swing EDT.</p>
 */
public class LookupPopupMenuItem extends PopupMenuItemHttpMessageContainer {

    private static final long serialVersionUID = 1L;

    private final LookupClient client = new LookupClient();

    public LookupPopupMenuItem() {
        super("Find disclosure contact");
    }

    @Override
    public boolean isSafe() {
        return true;
    }

    @Override
    protected void performAction(HttpMessage msg) {
        final String host = extractHost(msg);
        if (host == null || host.isBlank()) {
            showWarning("Could not determine a host from the selected message.");
            return;
        }

        // The blocking HTTP call must never run on the Swing EDT.
        Thread worker = new Thread(() -> {
            try {
                LookupResult result = client.lookup(host);
                final String text = formatResult(host, result);
                SwingUtilities.invokeLater(() -> showInfo(text));
            } catch (LookupException ex) {
                final String message = "Lookup failed for " + host + ":\n" + ex.getMessage();
                SwingUtilities.invokeLater(() -> showWarning(message));
            }
        }, "disclose-lookup-" + host);
        worker.setDaemon(true);
        worker.start();
    }

    /** Extracts the bare host from the request URI, degrading gracefully on error. */
    private static String extractHost(HttpMessage msg) {
        if (msg == null || msg.getRequestHeader() == null) {
            return null;
        }
        try {
            org.apache.commons.httpclient.URI uri = msg.getRequestHeader().getURI();
            if (uri == null) {
                return null;
            }
            String host = uri.getHost();
            return host != null ? host.trim() : null;
        } catch (URIException ex) {
            return null;
        }
    }

    /** Renders a concise, readable multi-line summary for a plain message dialog. */
    private static String formatResult(String host, LookupResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Disclosure contact lookup\n");
        sb.append("Host: ").append(host).append('\n');

        LookupResult.Attribution attribution = result.attribution();
        if (attribution != null && attribution.organization() != null
                && !attribution.organization().isBlank()) {
            sb.append("Organization: ").append(attribution.organization());
            if (attribution.jurisdiction() != null && !attribution.jurisdiction().isBlank()) {
                sb.append(" (").append(attribution.jurisdiction()).append(')');
            }
            sb.append('\n');
            if (attribution.parentCompany() != null && !attribution.parentCompany().isBlank()) {
                sb.append("Parent company: ").append(attribution.parentCompany()).append('\n');
            }
        }

        sb.append("Status: ").append(result.status()).append('\n');

        List<LookupResult.Contact> contacts = result.rankedContacts();
        if (contacts.isEmpty()) {
            String explanation = result.detailExplanation();
            sb.append('\n');
            if (explanation != null && !explanation.isBlank()) {
                sb.append(explanation);
            } else {
                sb.append("No disclosure contact found for this host.");
            }
        } else {
            sb.append("\nContacts (best first):\n");
            for (LookupResult.Contact c : contacts) {
                sb.append("  • ").append(c.type());
                if (!c.value().isBlank()) {
                    sb.append(" — ").append(c.value());
                }
                String badge = c.verified()
                        ? "verified"
                        : (c.confidence().isBlank() ? "" : c.confidence() + " confidence");
                if (!badge.isBlank()) {
                    sb.append(" [").append(badge).append(']');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static void showInfo(String text) {
        View view = View.getSingleton();
        if (view != null) {
            view.showMessageDialog(text);
        }
    }

    private static void showWarning(String text) {
        View view = View.getSingleton();
        if (view != null) {
            view.showWarningDialog(text);
        }
    }
}
