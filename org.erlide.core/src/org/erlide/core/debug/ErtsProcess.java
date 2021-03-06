/*******************************************************************************
 * Copyright (c) 2004 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.core.debug;

import java.io.File;
import java.io.IOException;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.eclipse.debug.core.model.RuntimeProcess;
import org.erlide.core.backend.BackendData;
import org.erlide.core.backend.ErtsWatcherRunnable;
import org.erlide.core.backend.runtimeinfo.RuntimeInfo;
import org.erlide.jinterface.ErlLogger;

public class ErtsProcess extends RuntimeProcess {

    private BackendData launchData;

    public ErtsProcess(final Process process, final BackendData data) {
        super(data.getLaunch(), process, data.getNodeName(), null);
        ErlLogger.debug("# create ErtsProcess: " + data.getNodeName());

        final RuntimeInfo info = data.getRuntimeInfo();
        final File workingDirectory = new File(info.getWorkingDir());
        startWatcher(info, workingDirectory, process);
    }

    /**
     * @return Returns the started.
     */
    public boolean isStarted() {
        return getLaunch() != null;
    }

    /**
     * Write something out to the node process.
     * 
     * @param value
     *            The system.
     * @throws IOException
     */
    public synchronized void writeToErlang(final String value)
            throws IOException {
        if (!isStarted()) {
            return;
        }
        final IStreamsProxy astreamsProxy = getStreamsProxy();
        if (astreamsProxy != null) {
            astreamsProxy.write(value);
        }
    }

    /**
     * if this isn't already stopped, try to stop it.
     * 
     * @throws Throwable
     */
    @Override
    protected void finalize() throws Throwable {
        terminate();
        super.finalize();
    }

    public void addStdListener(final IStreamListener dspHandler) {
        final IStreamsProxy streamsProxy = getStreamsProxy();
        if (streamsProxy != null) {
            streamsProxy.getOutputStreamMonitor().addListener(dspHandler);
        }
    }

    public void addErrListener(final IStreamListener errHandler) {
        final IStreamsProxy streamsProxy = getStreamsProxy();
        if (streamsProxy != null) {
            streamsProxy.getErrorStreamMonitor().addListener(errHandler);
        }
    }

    @Override
    protected void terminated() {
        ErlLogger.debug("ErtsProcess terminated: %s", getLabel());
        super.terminated();
        try {
            getLaunch().terminate();
        } catch (final DebugException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void terminate() throws DebugException {
        ErlLogger.debug("ErtsProcess will be terminated: %s", getLabel());
        super.terminate();
    }

    private void startWatcher(final RuntimeInfo info,
            final File workingDirectory, final Process process) {
        final Runnable watcher = new ErtsWatcherRunnable(info,
                workingDirectory, process);
        final Thread thread = new Thread(null, watcher, "ErtsProcess watcher");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

}
