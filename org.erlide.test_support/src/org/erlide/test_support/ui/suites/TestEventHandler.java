package org.erlide.test_support.ui.suites;

import org.erlide.core.backend.events.ErlangEvent;
import org.erlide.core.backend.events.EventHandler;

public class TestEventHandler extends EventHandler {

    private final TestResultsView view;

    public TestEventHandler(final TestResultsView view) {
        this.view = view;
    }

    @Override
    protected void doHandleEvent(final ErlangEvent event) throws Exception {
        if (!event.hasTopic("bterl")) {
            return;
        }
        if (view != null) {
            view.notifyEvent(event.data);
        }
    }

}
