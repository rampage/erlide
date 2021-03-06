/*******************************************************************************
 * Copyright (c) 2004 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.ui.properties;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.erlide.core.CoreScope;
import org.erlide.core.backend.BackendCore;
import org.erlide.core.backend.runtimeinfo.RuntimeInfo;
import org.erlide.core.internal.model.root.ProjectPreferencesConstants;
import org.erlide.core.model.root.IErlProject;
import org.erlide.jinterface.ErlLogger;

import com.bdaum.overlayPages.FieldEditorOverlayPage;

public class OldErlProjectPropertyPage extends FieldEditorOverlayPage {

    /**
     * Constructor for ErlProjectPropertyPage.
     */
    public OldErlProjectPropertyPage() {
        super("Erlang project properties", GRID);
        setPropertiesOnly();
    }

    @Override
    public void propertyChange(final PropertyChangeEvent event) {
        ErlLogger.debug("*+> " + event);
    }

    @Override
    protected void createFieldEditors() {
        final IProject prj = (IProject) getElement().getAdapter(IProject.class);

        try {
            prj.getFolder(new Path(".settings")).refreshLocal(
                    IResource.DEPTH_ONE, null);
        } catch (final CoreException e) {
        }

        final Composite fieldEditorParent = getFieldEditorParent();
        final ProjectDirectoryFieldEditor out = new ProjectDirectoryFieldEditor(
                ProjectPreferencesConstants.OUTPUT_DIR, "Output directory:",
                fieldEditorParent, prj);
        addField(out);

        final ProjectPathEditor src = new ProjectPathEditor(
                ProjectPreferencesConstants.SOURCE_DIRS, "Source directories:",
                "Select directory:", fieldEditorParent, prj);
        addField(src);

        final ProjectPathEditor inc = new ProjectPathEditor(
                ProjectPreferencesConstants.INCLUDE_DIRS,
                "Include directories:", "Select directory:", fieldEditorParent,
                prj);
        addField(inc);

        // IPreferenceStore ps = getPreferenceStore();
        // OldErlangProjectProperties props = new
        // OldErlangProjectProperties(prj);
        // List<String> tstDirs = props.getTestDirs();
        // String tstStr = PreferencesUtils.packList(tstDirs);
        // ps.setValue(ProjectPreferencesConstants.TEST_DIRS, tstStr);
        //
        // ProjectPathEditor tst = new ProjectPathEditor(
        // ProjectPreferencesConstants.TEST_DIRS,
        // "Test source directories:", "Select directory:",
        // fieldEditorParent, prj);
        // tst.setEnabled(false, fieldEditorParent);
        // addField(tst);

        final Collection<RuntimeInfo> rs = BackendCore.getRuntimeInfoManager()
                .getRuntimes();
        final String[][] runtimes = new String[rs.size()][2];
        final Iterator<RuntimeInfo> it = rs.iterator();
        for (int i = 0; i < rs.size(); i++) {
            runtimes[i][0] = it.next().getVersion().asMinor().toString();
            runtimes[i][1] = runtimes[i][0];
        }
        addField(new ComboFieldEditor(
                ProjectPreferencesConstants.RUNTIME_VERSION,
                "Runtime version:", runtimes, fieldEditorParent));
    }

    @Override
    protected String getPageId() {
        return "org.erlide.core";
    }

    public void init(final IWorkbench workbench) {
    }

    @Override
    public boolean performOk() {
        final IProject project = (IProject) getElement().getAdapter(
                IProject.class);
        final IErlProject erlProject = CoreScope.getModel().getErlangProject(
                project);
        erlProject.clearCaches();
        return super.performOk();
    }
}
