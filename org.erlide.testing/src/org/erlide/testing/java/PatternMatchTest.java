/*******************************************************************************
 * Copyright (c) 2004 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.testing.java;

import junit.framework.Assert;

import org.erlide.jinterface.Bindings;
import org.erlide.jinterface.ErlUtils;
import org.junit.Test;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangLong;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangString;

/**
 * 
 * 
 * @author Vlad Dumitrescu
 */
public class PatternMatchTest {

	@Test
	public void testFormatParser_simple() throws Exception {
		OtpErlangObject value = ErlUtils.format("~w", "a", "hej");
		OtpErlangObject expected = ErlUtils.parse("hej");
		Assert.assertEquals(expected, value);
	}

	@Test
	public void testFormatParser_simple_1() throws Exception {
		OtpErlangObject value = ErlUtils.format("~w", "");
		OtpErlangObject expected = ErlUtils.parse("~w");
		Assert.assertEquals(expected, value);
	}

	@Test
	public void testFormatParser_list() throws Exception {
		OtpErlangObject value = ErlUtils
				.format("[~w,2,~w]", "aa", "hej", "brr");
		OtpErlangObject expected = ErlUtils.parse("[hej,2,brr]");
		Assert.assertEquals(expected, value);
	}

	@Test
	public void testFormatParser_tuple() throws Exception {
		OtpErlangObject value = ErlUtils
				.format("{~w,2,~w}", "aa", "hej", "brr");
		OtpErlangObject expected = ErlUtils.parse("{hej,2,brr}");
		Assert.assertEquals(expected, value);
	}

	@Test
	public void testFormatParser_full() throws Exception {
		OtpErlangObject value = ErlUtils.format("[~w,{2,~w},5]", "aa", "hej",
				"brr");
		OtpErlangObject expected = ErlUtils.parse("[hej,{2,brr},5]");
		Assert.assertEquals(expected, value);
	}

	@Test
	public void testMatch_novar() throws Exception {
		OtpErlangObject p = ErlUtils.parse("[a, {b}]");
		OtpErlangObject t1 = ErlUtils.parse("[a, {b}]");
		Bindings r = ErlUtils.match(p, t1);
		Assert.assertNotNull(r);
	}

	@Test
	public void testMatch() throws Exception {
		Bindings r = ErlUtils.match("[W, V]", "[a, b]");
		Assert.assertEquals(r.get("W"), new OtpErlangAtom("a"));
		Assert.assertEquals(r.get("V"), new OtpErlangAtom("b"));
	}

	@Test
	public void testMatch_1() throws Exception {
		Bindings r = ErlUtils.match("[W, V]", "[\"a\", {[1, 2]}]");
		Assert.assertEquals(r.get("W"), new OtpErlangString("a"));
		Assert.assertEquals(r.get("V"), ErlUtils.parse("{[1, 2]}"));
	}

	@Test
	public void testMatch_same() throws Exception {
		Bindings r = ErlUtils.match("[W, {V}]", "[a, {a}]");
		Assert.assertEquals(r.get("W"), new OtpErlangAtom("a"));
	}

	@Test
	public void testMatch_any() throws Exception {
		Bindings r = ErlUtils.match("[_, {_}]", "[a, {b}]");
		Assert.assertNotNull(r);
	}

	@Test
	public void testMatch_same_fail() throws Exception {
		Bindings r = ErlUtils.match("[W, {W}]", "[a, {b}]");
		Assert.assertNull(r);
	}

	@Test
	public void testMatch_sig_a() throws Exception {
		Bindings r = ErlUtils.match("W:a", "zzz");
		Assert.assertEquals(r.get("W"), new OtpErlangAtom("zzz"));
	}

	@Test
	public void testMatch_sig_i() throws Exception {
		Bindings r = ErlUtils.match("W:i", "222");
		Assert.assertEquals(r.get("W"), new OtpErlangLong(222));
	}

	@Test
	public void testMatch_sig_fail() throws Exception {
		Bindings r = ErlUtils.match("W:i", "zzz");
		Assert.assertNull(r);
	}

}
