package org.erlide.core.backend;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.RegistryFactory;
import org.erlide.core.ErlangCore;
import org.erlide.core.common.SourcePathProvider;
import org.erlide.core.common.Util;
import org.erlide.jinterface.ErlLogger;

import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangTuple;
import com.google.common.collect.Lists;

public class BackendUtils {

    public static Collection<SourcePathProvider> getSourcePathProviders()
            throws CoreException {
        // TODO should be cached and listening to plugin changes?
        final List<SourcePathProvider> result = Lists.newArrayList();
        final IConfigurationElement[] elements = getSourcepathConfigurationElements();
        for (final IConfigurationElement element : elements) {
            final SourcePathProvider provider = (SourcePathProvider) element
                    .createExecutableExtension("class");
            result.add(provider);
        }
        return result;
    }

    public static String getErlideNodeNameTag() {
        String fUniqueId;
        final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        final String location = root.getLocation().toPortableString();
        final String user = System.getProperty("user.name");
        final String timestamp = Long
                .toHexString(System.currentTimeMillis() & 0xFFFFFF);
        fUniqueId = Long.toHexString(location.hashCode() & 0xFFFFF) + "_"
                + user + "_" + timestamp;
        return fUniqueId.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    public static String getBeamModuleName(final String path) {
        return getBeamModuleName(new Path(path));
    }

    public static String getBeamModuleName(final IPath path) {
        if (path.getFileExtension() != null
                && "beam".equals(path.getFileExtension())) {
            return path.removeFileExtension().lastSegment();
        }
        return null;
    }

    private abstract static class SPPMethod {
        protected SourcePathProvider target;

        public void setTarget(final SourcePathProvider spp) {
            target = spp;
        }

        abstract public Collection<IPath> call(IProject project);
    }

    public static Collection<String> getExtraSourcePathsForBuild(
            final IProject project) {
        return BackendUtils.getExtraSourcePathsGeneric(project,
                new SPPMethod() {
                    @Override
                    public Collection<IPath> call(final IProject myProject) {
                        return target.getSourcePathsForBuild(myProject);
                    }
                });
    }

    public static Collection<String> getExtraSourcePathsForModel(
            final IProject project) {
        return BackendUtils.getExtraSourcePathsGeneric(project,
                new SPPMethod() {
                    @Override
                    public Collection<IPath> call(final IProject myProject) {
                        return target.getSourcePathsForModel(myProject);
                    }
                });
    }

    public static Collection<String> getExtraSourcePathsForExecution(
            final IProject project) {
        return BackendUtils.getExtraSourcePathsGeneric(project,
                new SPPMethod() {
                    @Override
                    public Collection<IPath> call(final IProject myProject) {
                        return target.getSourcePathsForExecution(myProject);
                    }
                });
    }

    private static Collection<String> getExtraSourcePathsGeneric(
            final IProject project, final SPPMethod method) {
        final List<String> result = Lists.newArrayList();
        Collection<SourcePathProvider> spps;
        try {
            spps = getSourcePathProviders();
            for (final SourcePathProvider spp : spps) {
                method.setTarget(spp);
                final Collection<IPath> paths = method.call(project);
                for (final IPath p : paths) {
                    result.add(p.toString());
                }
            }
        } catch (final Exception e) {
            ErlLogger.error(e);
        }
        return result;
    }

    public static Collection<String> getExtraSourcePaths() {
        final IProject[] projects = ResourcesPlugin.getWorkspace().getRoot()
                .getProjects();
        final List<String> result = Lists.newArrayList();
        for (final IProject project : projects) {
            result.addAll(getExtraSourcePathsForModel(project));
        }
        return result;
    }

    public static OtpErlangObject ok(final OtpErlangObject v0) {
        if (!(v0 instanceof OtpErlangTuple)) {
            return v0;
        }
        final OtpErlangTuple v = (OtpErlangTuple) v0;
        if (Util.isOk(v)) {
            return v.elementAt(1);
        }
        return v;
    }

    public static IConfigurationElement[] getSourcepathConfigurationElements() {
        final IExtensionRegistry reg = RegistryFactory.getRegistry();
        return reg.getConfigurationElementsFor(ErlangCore.PLUGIN_ID,
                "sourcePathProvider");
    }

    public static IConfigurationElement[] getCodepathConfigurationElements() {
        final IExtensionRegistry reg = RegistryFactory.getRegistry();
        return reg.getConfigurationElementsFor(ErlangCore.PLUGIN_ID,
                "codepath");
    }

    public static IExtensionPoint getCodepathExtension() {
        final IExtensionRegistry reg = Platform.getExtensionRegistry();
        return reg.getExtensionPoint(ErlangCore.PLUGIN_ID, "codepath");
    }

}
