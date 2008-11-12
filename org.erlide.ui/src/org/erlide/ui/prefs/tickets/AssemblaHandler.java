/*******************************************************************************
 * Copyright (c) 2008 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution.
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.ui.prefs.tickets;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class AssemblaHandler extends TicketHandlerImpl {

	private static final String MYURL = "http://www.assembla.com/spaces/erlide/tickets/";
	private static final String LOGINURL = "https://www.assembla.com/users/login";

	private static byte[] userHash = { (byte) 248, (byte) 254, (byte) 185,
			(byte) 157, (byte) 211, 11, 33, (byte) 206, (byte) 147, (byte) 211,
			(byte) 177, 10, 93, (byte) 209, (byte) 151, 59 };

	private boolean checkUser(String user) {
		try {
			MessageDigest algorithm = MessageDigest.getInstance("MD5");
			algorithm.reset();
			algorithm.update(user.getBytes());
			byte messageDigest[] = algorithm.digest();
			return Arrays.equals(messageDigest, userHash);
		} catch (NoSuchAlgorithmException nsae) {
		}
		return false;
	}

	public String infoToMessage(TicketInfo info) {
		return "<ticket><summary>" + info.summary + "</summary></ticket>";
	}

	public TicketStatus parseMessage(String message) {
		boolean ok = false;
		int id = 0;

		TicketStatus result = new TicketStatus(ok, id);
		return result;
	}

	public URL getLoginURL(String user, String pass)
			throws MalformedURLException {
		if (checkUser(user)) {
			String str = String.format(MYURL, user, pass);
			return new URL(str);
		}
		return null;
	}

}