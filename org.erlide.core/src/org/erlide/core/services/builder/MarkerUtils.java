package org.erlide.core.services.builder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.erlide.core.CoreScope;
import org.erlide.core.ErlangPlugin;
import org.erlide.core.common.Tuple;
import org.erlide.core.common.Util;
import org.erlide.core.model.erlang.IErlComment;
import org.erlide.core.model.erlang.IErlFunction;
import org.erlide.core.model.erlang.IErlModule;
import org.erlide.core.model.root.ErlModelException;
import org.erlide.core.model.root.IErlModel;
import org.erlide.core.model.root.IErlProject;
import org.erlide.core.model.root.ISourceRange;
import org.erlide.core.rpc.IRpcCallSite;
import org.erlide.jinterface.ErlLogger;
import org.erlide.jinterface.util.ErlUtils;

import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangLong;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangRangeException;
import com.ericsson.otp.erlang.OtpErlangTuple;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public final class MarkerUtils {

    private static final String FIXME = "FIXME";
    private static final String XXX = "XXX";
    private static final String TODO = "TODO";
    // Copied from org.eclipse.ui.ide (since we don't want ui code in core)
    public static final String PATH_ATTRIBUTE = "org.eclipse.ui.views.markers.path";//$NON-NLS-1$
    public static final String DIALYZE_WARNING_MARKER = ErlangPlugin.PLUGIN_ID
            + ".dialyzewarningmarker";

    private MarkerUtils() {
    }

    public static final String PROBLEM_MARKER = ErlangPlugin.PLUGIN_ID
            + ".problemmarker";
    public static final String TASK_MARKER = ErlangPlugin.PLUGIN_ID
            + ".taskmarker";

    public static void addMarker(final IResource file, final String path,
            final IResource compiledFile, final String errorDesc,
            final int lineNumber, final int severity, final String errorVar) {
        addProblemMarker(file, path, compiledFile, errorDesc, lineNumber,
                severity);
    }

    public static void addTaskMarker(final IResource file,
            final IResource compiledFile, final String message, int lineNumber,
            final int priority) {
        try {
            final IMarker marker = file.createMarker(TASK_MARKER);
            marker.setAttribute(IMarker.MESSAGE, message);
            marker.setAttribute(IMarker.PRIORITY, priority);
            marker.setAttribute(IMarker.SOURCE_ID, compiledFile.getFullPath()
                    .toString());
            if (lineNumber == -1) {
                lineNumber = 1;
            }
            marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
        } catch (final CoreException e) {
        }
    }

    /**
     * Add error markers from a list of error tuples
     * 
     * @param resource
     * @param errorList
     */
    public static void addErrorMarkers(final IResource resource,
            final OtpErlangList errorList) {
        final OtpErlangObject[] messages = errorList.elements();
        final Map<String, List<OtpErlangTuple>> groupedMessages = groupMessagesByFile(messages);

        for (final Entry<String, List<OtpErlangTuple>> entry : groupedMessages
                .entrySet()) {
            final String fileName = entry.getKey();
            final IResource res = findResourceForFileName(resource, entry,
                    fileName);

            for (final OtpErlangTuple data : entry.getValue()) {
                addAnnotationForMessage(resource, fileName, res, data);
            }
        }
    }

    private static IResource findResourceForFileName(final IResource resource,
            final Entry<String, List<OtpErlangTuple>> entry,
            final String fileName) {
        IResource res = resource;
        if (!BuilderHelper
                .samePath(resource.getLocation().toString(), fileName)) {
            final IProject project = resource.getProject();
            res = BuilderHelper.findResourceByLocation(project, fileName);
            if (res == null) {
                try {
                    final IErlModel model = CoreScope.getModel();
                    final IErlProject erlProject = model.findProject(project);
                    if (erlProject != null) {
                        final IErlModule includeFile = model
                                .findIncludeFromProject(erlProject, fileName,
                                        fileName,
                                        IErlModel.Scope.REFERENCED_PROJECTS);
                        ErlLogger.debug("inc::" + fileName + " "
                                + resource.getName() + " "
                                + erlProject.getName());
                        ErlLogger.debug("    " + entry.getValue());

                        if (includeFile == null) {
                            res = resource;
                        } else {
                            res = includeFile.getResource();
                            // FIXME is this right?
                        }
                    } else {
                        res = resource;
                    }
                } catch (final Exception e) {
                    ErlLogger.warn(e);
                }
            }
        }
        return res;
    }

    private static void addAnnotationForMessage(final IResource resource,
            final String fileName, final IResource res,
            final OtpErlangTuple data) {
        int line = 0;
        if (data.elementAt(0) instanceof OtpErlangLong) {
            try {
                line = ((OtpErlangLong) data.elementAt(0)).intValue();
            } catch (final OtpErlangRangeException e) {
            }
        }
        int sev = IMarker.SEVERITY_INFO;
        try {
            switch (((OtpErlangLong) data.elementAt(3)).intValue()) {
            case 0:
                sev = IMarker.SEVERITY_ERROR;
                break;
            case 1:
                sev = IMarker.SEVERITY_WARNING;
                break;
            default:
                sev = IMarker.SEVERITY_INFO;
                break;
            }
        } catch (final OtpErlangRangeException e) {
        }

        String msg = ErlUtils.asString(data.elementAt(2));
        if (msg.length() > 1000) {
            msg = msg.substring(0, 1000) + "...";
        }
        if (res != null) {
            addMarker(res, fileName, resource, msg, line, sev, "");
        } else {
            addMarker(resource.getProject(), null, null, "can't find "
                    + fileName, 0, IMarker.SEVERITY_ERROR, "");
            addMarker(resource, null, null, "?? " + msg, line, sev, "");
        }
    }

    private static Map<String, List<OtpErlangTuple>> groupMessagesByFile(
            final OtpErlangObject[] messages) {
        final Map<String, List<OtpErlangTuple>> result = Maps.newHashMap();
        for (final OtpErlangObject msg : messages) {
            final OtpErlangTuple tuple = (OtpErlangTuple) msg;
            final String fileName = ErlUtils.asString(tuple.elementAt(1));
            addMessage(result, fileName, tuple);
        }
        return result;
    }

    private static void addMessage(final Map<String, List<OtpErlangTuple>> map,
            final String key, final OtpErlangTuple tuple) {
        List<OtpErlangTuple> list = map.get(key);
        if (list == null) {
            list = Lists.newArrayList();
            map.put(key, list);
        }
        list.add(tuple);
    }

    public static void addProblemMarker(final IResource resource,
            final String path, final IResource compiledFile,
            final String message, int lineNumber, final int severity) {
        try {
            final IMarker marker = resource.createMarker(PROBLEM_MARKER);
            marker.setAttribute(IMarker.MESSAGE, message);
            marker.setAttribute(IMarker.SEVERITY, severity);
            if (path != null && !new Path(path).equals(resource.getLocation())) {
                marker.setAttribute(MarkerUtils.PATH_ATTRIBUTE, path);
            }
            if (compiledFile != null) {
                marker.setAttribute(IMarker.SOURCE_ID, compiledFile
                        .getFullPath().toString());
            }
            if (lineNumber == -1) {
                lineNumber = 1;
            }
            marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
        } catch (final CoreException e) {
        }
    }

    public static IMarker[] getProblemsFor(final IResource resource) {
        return getMarkersFor(resource, PROBLEM_MARKER);
    }

    public static IMarker[] getTasksFor(final IResource resource) {
        return getMarkersFor(resource, TASK_MARKER);
    }

    private static IMarker[] getMarkersFor(final IResource resource,
            final String type) {
        try {
            if (resource != null && resource.exists()) {
                return resource.findMarkers(type, false,
                        IResource.DEPTH_INFINITE);
            }
        } catch (final CoreException e) {
            // assume there are no tasks
        }
        return new IMarker[0];
    }

    public static void removeProblemsFor(final IResource resource) {
        removeMarkerFor(resource, PROBLEM_MARKER);
    }

    public static void removeTasksFor(final IResource resource) {
        removeMarkerFor(resource, TASK_MARKER);
    }

    private static void removeMarkerFor(final IResource resource,
            final String type) {
        try {
            if (resource != null && resource.exists()) {
                resource.deleteMarkers(type, false, IResource.DEPTH_INFINITE);
            }
        } catch (final CoreException e) {
            // assume there were no problems
        }
    }

    public static void removeProblemsAndTasksFor(final IResource resource) {
        try {
            if (resource != null && resource.exists()) {
                resource.deleteMarkers(PROBLEM_MARKER, false,
                        IResource.DEPTH_INFINITE);
                resource.deleteMarkers(TASK_MARKER, false,
                        IResource.DEPTH_INFINITE);
            }
        } catch (final CoreException e) {
            // assume there were no problems
        }
    }

    public static void removeDialyzerMarkers(final IResource resource) {
        try {
            resource.deleteMarkers(DIALYZE_WARNING_MARKER, true,
                    IResource.DEPTH_INFINITE);
        } catch (final CoreException e) {
            e.printStackTrace();
        }
    }

    public static boolean haveDialyzerMarkers(final IResource resource) {
        try {
            if (resource.isAccessible()) {
                final IMarker[] markers = resource.findMarkers(
                        DIALYZE_WARNING_MARKER, true, IResource.DEPTH_INFINITE);
                return markers != null && markers.length > 0;
            }
        } catch (final CoreException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void deleteMarkers(final IResource resource) {
        try {
            resource.deleteMarkers(PROBLEM_MARKER, false, IResource.DEPTH_ZERO);
            resource.deleteMarkers(TASK_MARKER, false, IResource.DEPTH_ZERO);
            if (resource instanceof IFile) {
                deleteMarkersWithCompiledFile(resource.getProject(),
                        (IFile) resource);
                // should we delete markers for dependent hrl files?
            }
        } catch (final CoreException ce) {
        }
    }

    private static void deleteMarkersWithCompiledFile(final IProject project,
            final IFile file) {
        if (!project.isAccessible()) {
            return;
        }
        try {
            for (final IMarker m : project.findMarkers(PROBLEM_MARKER, true,
                    IResource.DEPTH_INFINITE)) {
                final Object source_id = m.getAttribute(IMarker.SOURCE_ID);
                if (source_id != null && source_id instanceof String
                        && source_id.equals(file.getFullPath().toString())) {
                    try {
                        m.delete();
                    } catch (final CoreException e) {
                        // not much to do
                    }
                }
            }
            for (final IMarker m : project.findMarkers(TASK_MARKER, true,
                    IResource.DEPTH_INFINITE)) {
                final Object source_id = m.getAttribute(IMarker.SOURCE_ID);
                if (source_id != null && source_id instanceof String
                        && source_id.equals(file.getFullPath().toString())) {
                    try {
                        m.delete();
                    } catch (final CoreException e) {
                        // not much to do
                    }
                }
            }
        } catch (final CoreException e) {
            // not much to do
        }
    }

    public void createProblemFor(final IResource resource,
            final IErlFunction erlElement, final String message,
            final int problemSeverity) throws CoreException {
        try {
            final IMarker marker = resource.createMarker(PROBLEM_MARKER);
            final int severity = problemSeverity;

            final ISourceRange range = erlElement == null ? null : erlElement
                    .getNameRange();
            final int start = range == null ? 0 : range.getOffset();
            final int end = range == null ? 1 : start + range.getLength();
            marker.setAttributes(
                    new String[] { IMarker.MESSAGE, IMarker.SEVERITY,
                            IMarker.CHAR_START, IMarker.CHAR_END },
                    new Object[] { message, Integer.valueOf(severity),
                            Integer.valueOf(start), Integer.valueOf(end) });
        } catch (final CoreException e) {
            throw e;
        }
    }

    public static void addDialyzerWarningMarkersFromResultList(
            final IErlProject project, final IRpcCallSite backend,
            final OtpErlangList result) {
        if (result == null) {
            return;
        }
        final IProject p = project.getWorkspaceProject();
        for (final OtpErlangObject i : result) {
            final OtpErlangTuple t = (OtpErlangTuple) i;
            final OtpErlangTuple fileLine = (OtpErlangTuple) t.elementAt(1);
            final String filename = Util.stringValue(fileLine.elementAt(0));
            final OtpErlangLong lineL = (OtpErlangLong) fileLine.elementAt(1);
            int line = 1;
            try {
                line = lineL.intValue();
            } catch (final OtpErlangRangeException e) {
                ErlLogger.error(e);
            }
            String s = ErlideDialyze.formatWarning(backend, t).trim();
            final int j = s.indexOf(": ");
            if (j != -1) {
                s = s.substring(j + 1);
            }
            addDialyzerWarningMarker(p, filename, line, s);
        }
    }

    public static IMarker createSearchResultMarker(final IErlModule module,
            final String type, final int offset, final int length)
            throws CoreException {
        boolean setPath = false;
        IResource resource = module.getCorrespondingResource();
        if (resource == null) {
            resource = ResourcesPlugin.getWorkspace().getRoot();
            setPath = true;
        }
        final IMarker marker = resource.createMarker(type);
        marker.setAttribute(IMarker.CHAR_START, offset);
        marker.setAttribute(IMarker.CHAR_END, offset + length);
        if (setPath) {
            marker.setAttribute(PATH_ATTRIBUTE, module.getFilePath());
        }
        return marker;
    }

    public static void addDialyzerWarningMarker(final IResource file,
            final String path, final String message, int lineNumber,
            final int severity) {
        try {
            final IMarker marker;
            if (file != null) {
                marker = file.createMarker(DIALYZE_WARNING_MARKER);
            } else {
                final IWorkspaceRoot workspaceRoot = ResourcesPlugin
                        .getWorkspace().getRoot();
                marker = workspaceRoot.createMarker(DIALYZE_WARNING_MARKER);
                marker.setAttribute(PATH_ATTRIBUTE, path);
            }
            marker.setAttribute(IMarker.MESSAGE, message);
            marker.setAttribute(IMarker.SEVERITY, severity);
            if (lineNumber == -1) {
                lineNumber = 1;
            }
            marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
        } catch (final CoreException e) {
        }
    }

    public static void addDialyzerWarningMarker(final IProject project,
            final String filename, final int line, final String message) {
        final IPath projectPath = project.getLocation();
        final String projectPathString = projectPath.toPortableString();
        IResource file;
        if (filename.startsWith(projectPathString)) {
            final String relFilename = filename.substring(projectPathString
                    .length());
            final IPath relPath = Path.fromPortableString(relFilename);
            file = project.findMember(relPath);
        } else {
            file = null;
        }
        MarkerUtils.addDialyzerWarningMarker(file, filename, message, line,
                IMarker.SEVERITY_WARNING);
    }

    public static void createTaskMarkers(final IProject project,
            final IResource resource) {
        final IErlProject p = CoreScope.getModel().findProject(project);
        if (p != null) {
            try {
                // getMarkersFor(resource, p);
                getNoScanMarkersFor(resource, p);
            } catch (final ErlModelException e) {
            }
        }

    }

    @SuppressWarnings("unused")
    private static void getMarkersFor(final IResource resource,
            final IErlProject p) throws ErlModelException {
        final IErlModule m = p.getModule(resource.getName());
        if (m == null) {
            return;
        }
        // m.getScanner(); FIXME why did we need this?
        final Collection<IErlComment> cl = m.getComments();
        for (final IErlComment c : cl) {
            final String text = c.getName();
            final int line = c.getLineStart();
            mkMarker(resource, line, text, TODO, IMarker.PRIORITY_NORMAL);
            mkMarker(resource, line, text, XXX, IMarker.PRIORITY_NORMAL);
            mkMarker(resource, line, text, FIXME, IMarker.PRIORITY_HIGH);
        }
        // m.disposeScanner(); FIXME why did we need this?
    }

    private static void getNoScanMarkersFor(final IResource resource,
            final IErlProject p) throws ErlModelException {
        if (!(resource instanceof IFile)) {
            return;
        }
        final IFile file = (IFile) resource;
        InputStream input;
        try {
            input = file.getContents();
            final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input));
            try {
                String line = reader.readLine();
                final List<Tuple<String, Integer>> cl = new ArrayList<Tuple<String, Integer>>();
                int numline = 0;
                while (line != null) {
                    if (line.matches("^[^%]*%+[ \t]*(TODO|XXX|FIXME).*")) {
                        cl.add(new Tuple<String, Integer>(line, numline));
                    }
                    numline++;
                    line = reader.readLine();
                }

                for (final Tuple<String, Integer> c : cl) {
                    mkMarker(resource, c.o2, c.o1, TODO,
                            IMarker.PRIORITY_NORMAL);
                    mkMarker(resource, c.o2, c.o1, XXX, IMarker.PRIORITY_NORMAL);
                    mkMarker(resource, c.o2, c.o1, FIXME, IMarker.PRIORITY_HIGH);
                }
            } finally {
                reader.close();
            }
        } catch (final CoreException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static void mkMarker(final IResource resource, final int line,
            final String text, final String tag, final int prio) {
        if (text.contains(tag)) {
            final int ix = text.indexOf(tag);
            final String msg = text.substring(ix);
            int dl = 0;
            for (int i = 0; i < ix; i++) {
                if (text.charAt(i) == '\n') {
                    dl++;
                }
            }
            addTaskMarker(resource, resource, msg, line + 1 + dl, prio);
        }
    }

}
