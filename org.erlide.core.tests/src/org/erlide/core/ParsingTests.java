/**
 * 
 */
package org.erlide.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.erlide.core.erlang.TestingSupport;
import org.erlide.core.internal.model.erlang.ErlideScanner;
import org.erlide.core.model.erlang.ErlangToolkit;
import org.erlide.core.model.erlang.IErlModule;
import org.erlide.core.model.root.ErlModelException;
import org.erlide.core.model.root.IErlElement;
import org.erlide.core.model.root.IErlModel;
import org.erlide.core.model.root.IErlParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author jakob
 * 
 */
public class ParsingTests {

    IErlModule module;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        final IErlModel model = CoreScope.getModel();
        module = model.getModuleFromText(model, "testing", "", null);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
        module.dispose();
        module = null;
    }

    private boolean parse(final String s) {
        final String scannerModuleName = ErlangToolkit
                .createScannerModuleName(module);
        ErlideScanner.initialScan(scannerModuleName, "", s, false);
        final IErlParser parser = CoreScope.getModel().getParser();
        return parser.parse(module, scannerModuleName, false, "", false);
    }

    @Test
    public void parseCompileDirective() throws ErlModelException {
        final String sourceContent = "[inline,{hipe,[{regalloc,linear_scan}]}]";
        final String source = "-compile(" + sourceContent + ").";
        assertTrue(parse(source));
        final IErlElement attribute = TestingSupport.createErlAttribute(module,
                "compile", null, sourceContent, 0, 50);
        final List<IErlElement> expected = new ArrayList<IErlElement>(1);
        expected.add(attribute);
        final Collection<IErlElement> actual = module.getChildren();
        // assertEquals(expected, actual);
        assertEquals(expected.toString(), actual.toString());
    }

}
