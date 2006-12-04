/*******************************************************************************
 * Copyright (c) 2004 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution.
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.runtime.backend.internal;

import java.io.IOException;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.IStreamListener;

public class StandaloneBackend extends AbstractBackend {

	@Override
	public void addProject(String project) {
		System.out.println("$ add project " + project + " to remote "
				+ getLabel());
	}

	@Override
	public void connect() {
		doConnect(getLabel());
	}

	@Override
	public void sendToDefaultShell(String msg) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void sendToShell(String str) {
		// TODO Auto-generated method stub

	}

	@Override
	public void addStdListener(IStreamListener dsp) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isManaged() {
		return false;
	}

	@Override
	public ILaunch initialize() {
		return null;
	}

}
