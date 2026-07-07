package io.disclose.zaplookup;

import org.parosproxy.paros.extension.ExtensionAdaptor;
import org.parosproxy.paros.extension.ExtensionHook;

/**
 * ZAP extension entry point for Disclosure Contact Lookup.
 *
 * <p>Registers a right-click popup menu item that resolves the selected HTTP
 * message's host to its security-disclosure contact via lookup.disclose.io.
 * The menu item is only registered when ZAP is running with a view (GUI).</p>
 */
public class ExtensionZapLookup extends ExtensionAdaptor {

    public static final String NAME = "ExtensionZapLookup";

    public ExtensionZapLookup() {
        super(NAME);
    }

    @Override
    public String getUIName() {
        return "Disclosure Contact Lookup";
    }

    @Override
    public String getDescription() {
        return "Right-click a host to find its security-disclosure contact via lookup.disclose.io.";
    }

    @Override
    public void hook(ExtensionHook extensionHook) {
        super.hook(extensionHook);
        if (hasView()) {
            extensionHook.getHookMenu().addPopupMenuItem(new LookupPopupMenuItem());
        }
    }

    @Override
    public boolean canUnload() {
        return true;
    }
}
