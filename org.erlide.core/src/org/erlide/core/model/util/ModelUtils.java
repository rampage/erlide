package org.erlide.core.model.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.erlide.core.CoreScope;
import org.erlide.core.backend.BackendException;
import org.erlide.core.common.StringUtils;
import org.erlide.core.internal.model.root.SourceRange;
import org.erlide.core.model.erlang.IErlFunction;
import org.erlide.core.model.erlang.IErlImport;
import org.erlide.core.model.erlang.IErlModule;
import org.erlide.core.model.erlang.IErlPreprocessorDef;
import org.erlide.core.model.erlang.IErlTypespec;
import org.erlide.core.model.root.ErlModelException;
import org.erlide.core.model.root.IErlElement;
import org.erlide.core.model.root.IErlExternal;
import org.erlide.core.model.root.IErlModel;
import org.erlide.core.model.root.IErlProject;
import org.erlide.core.model.root.IOpenable;
import org.erlide.core.model.root.IParent;
import org.erlide.core.model.root.ISourceRange;
import org.erlide.core.model.root.IErlElement.Kind;
import org.erlide.core.rpc.IRpcCallSite;
import org.erlide.core.services.search.ErlideOpen;
import org.erlide.core.services.search.OpenResult;
import org.erlide.jinterface.ErlLogger;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangLong;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangRangeException;
import com.ericsson.otp.erlang.OtpErlangTuple;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class ModelUtils {

    private static final String DELIMITER = "<>";

    public static IErlTypespec findTypespec(final IErlModule module,
            final String name) throws CoreException {
        IErlTypespec typespec = module.findTypespec(name);
        if (typespec != null) {
            return typespec;
        }
        final List<IErlModule> includedFiles = module.findAllIncludedFiles();
        for (final IErlModule includedFile : includedFiles) {
            typespec = includedFile.findTypespec(name);
            if (typespec != null) {
                return typespec;
            }
        }
        return null;
    }

    public static String getExternalModulePath(final IErlModule module) {
        final List<String> result = Lists.newArrayList();
        IErlElement element = module;
        final IErlModel model = CoreScope.getModel();
        while (element != model) {
            if (element instanceof IErlExternal) {
                final IErlExternal external = (IErlExternal) element;
                result.add(external.getExternalName());
            } else {
                result.add(element.getName());
            }
            element = (IErlElement) element.getParent();
        }
        return StringUtils.join(DELIMITER, Lists.reverse(result));
    }

    private static IErlExternal getElementWithExternalName(
            final IParent parent, final String segment)
            throws ErlModelException {
        for (final IErlElement i : parent.getChildrenOfKind(Kind.EXTERNAL)) {
            final IErlExternal external = (IErlExternal) i;
            final String externalName = external.getExternalName();
            ErlLogger
                    .debug("externalName %s segment %s", externalName, segment);
            if (externalName.equals(segment)) {
                return external;
            }
        }
        return null;
    }

    public static IErlModule getModuleFromExternalModulePath(
            final String modulePath) throws ErlModelException {
        final List<String> path = StringUtils.split(DELIMITER, modulePath);
        final IErlModel model = CoreScope.getModel();
        model.open(null);
        final IErlElement childNamed = model.getChildNamed(path.get(0));
        ErlLogger.debug(">>childNamed %s", childNamed == null ? "<null>"
                : childNamed.getName());
        if (childNamed instanceof IParent) {
            IParent parent = (IParent) childNamed;
            final int n = path.size() - 1;
            for (int i = 1;; i++) {
                if (parent == null) {
                    break;
                }
                if (parent instanceof IOpenable) {
                    final IOpenable openable = (IOpenable) parent;
                    openable.open(null);
                }
                if (i == n) {
                    break;
                }
                parent = getElementWithExternalName(parent, path.get(i));
                ErlLogger.debug(">>parent %s", parent);
            }
            if (parent != null) {
                final IErlElement child = parent.getChildNamed(path.get(n));
                if (child instanceof IErlModule) {
                    return (IErlModule) child;
                }
            }
        }
        return null;
    }

    public static List<String> findModulesWithPrefix(final String prefix,
            final IErlProject project, final boolean checkExternals)
            throws ErlModelException {
        final List<String> result = Lists.newArrayList();
        final Set<String> names = Sets.newHashSet();
        addModuleNamesWithPrefix(prefix, result, names, project.getModules());
        for (final IErlProject p : project.getReferencedProjects()) {
            if (p != null) {
                p.open(null);
                addModuleNamesWithPrefix(prefix, result, names, p.getModules());
            }
        }
        if (checkExternals) {
            addModuleNamesWithPrefix(prefix, result, names,
                    project.getExternalModules());
        }
        return result;
    }

    public static String resolveMacroValue(final String definedName,
            final IErlModule m) {
        if ("?MODULE".equals(definedName)) {
            return m.getModuleName();
        }
        final IErlPreprocessorDef def = m.findPreprocessorDef(
                StringUtils.withoutInterrogationMark(definedName),
                Kind.MACRO_DEF);
        if (def != null) {
            final String extra = def.getExtra();
            final int p = extra.indexOf(',');
            if (p != -1) {
                final String s = extra.substring(p + 1).trim();
                if (s.length() > 0) {
                    return s;
                }
            }
        }
        return definedName;
    }

    public static IErlFunction findFunction(String moduleName,
            final ErlangFunction erlangFunction, final String modulePath,
            final IErlProject project, final IErlModel.Scope scope,
            final IErlModule module) throws CoreException {
        if (moduleName != null) {
            moduleName = resolveMacroValue(moduleName, module);
            final IErlModule module2 = findModule(project, moduleName,
                    modulePath, scope);
            if (module2 != null) {
                module2.open(null);
                final IErlFunction function = module2
                        .findFunction(erlangFunction);
                if (function != null) {
                    return function;
                }
                return null;
            }
        }
        return null;
    }

    public static IErlModule findModule(final IErlProject project,
            final String moduleName, final String modulePath,
            final IErlModel.Scope scope) throws ErlModelException {
        final IErlModel model = CoreScope.getModel();
        if (project != null) {
            return model.findModuleFromProject(project, moduleName, modulePath,
                    scope);
        }
        if (scope == IErlModel.Scope.ALL_PROJECTS) {
            return model.findModule(moduleName, modulePath);
        }
        return null;
    }

    public static IErlElement findTypeDef(final IErlModule module,
            String moduleName, final String typeName, final String modulePath,
            final IErlProject project, final IErlModel.Scope scope)
            throws CoreException {
        moduleName = resolveMacroValue(moduleName, module);
        final IErlModule module2 = findModule(project, moduleName, modulePath,
                scope);
        if (module2 != null) {
            module2.open(null);
            return module2.findTypespec(typeName);
        }
        return null;
    }

    public static IErlPreprocessorDef findPreprocessorDef(
            final Collection<IErlProject> projects, final String moduleName,
            final String definedName, final IErlElement.Kind kind)
            throws CoreException, BackendException {
        for (final IErlProject project : projects) {
            if (project != null) {
                final IErlModule module = project.getModule(moduleName);
                if (module != null) {
                    final IErlPreprocessorDef def = findPreprocessorDef(module,
                            definedName, kind);
                    if (def != null) {
                        return def;
                    }
                }
            }
        }
        return null;
    }

    public static IErlPreprocessorDef findPreprocessorDef(
            final IErlModule module, final String definedName,
            final IErlElement.Kind kind) throws CoreException {
        String unquoted = StringUtils.unquote(definedName);
        final Set<String> names = new HashSet<String>(3);
        if (kind == Kind.RECORD_DEF) {
            while (names.add(unquoted)) {
                unquoted = resolveMacroValue(unquoted, module);
            }
        } else {
            names.add(unquoted);
        }
        names.add(definedName);
        final List<IErlModule> allIncludedFiles = module.findAllIncludedFiles();
        allIncludedFiles.add(0, module);
        for (final IErlModule includedFile : allIncludedFiles) {
            for (final String name : names) {
                includedFile.open(null);
                final IErlPreprocessorDef preprocessorDef = includedFile
                        .findPreprocessorDef(name, kind);
                if (preprocessorDef != null) {
                    return preprocessorDef;
                }
            }
        }
        return null;
    }

    public static List<OtpErlangObject> getImportsAsList(final IErlModule mod) {
        if (mod == null) {
            return NO_IMPORTS;
        }
        final Collection<IErlImport> imports = mod.getImports();
        if (imports.isEmpty()) {
            return NO_IMPORTS;
        }
        final List<OtpErlangObject> result = new ArrayList<OtpErlangObject>(
                imports.size());
        for (final IErlImport i : imports) {
            final Collection<ErlangFunction> functions = i.getFunctions();
            final OtpErlangObject funsT[] = new OtpErlangObject[functions
                    .size()];
            int j = 0;
            for (final ErlangFunction f : functions) {
                funsT[j] = f.getNameArityTuple();
                j++;
            }
            final OtpErlangTuple modFunsT = new OtpErlangTuple(
                    new OtpErlangObject[] {
                            new OtpErlangAtom(i.getImportModule()),
                            new OtpErlangList(funsT) });
            result.add(modFunsT);
        }
        return result;
    }

    public static List<IErlPreprocessorDef> getAllPreprocessorDefs(
            final IErlModule module, final IErlElement.Kind kind)
            throws CoreException {
        final List<IErlPreprocessorDef> result = Lists.newArrayList();
        final List<IErlModule> modulesWithIncludes = module
                .findAllIncludedFiles();
        modulesWithIncludes.add(module);
        for (final IErlModule m : modulesWithIncludes) {
            result.addAll(m.getPreprocessorDefs(kind));
        }
        return result;
    }

    public static final ArrayList<OtpErlangObject> NO_IMPORTS = new ArrayList<OtpErlangObject>(
            0);

    private static void addModuleNamesWithPrefix(final String prefix,
            final List<String> result, final Set<String> names,
            final Collection<IErlModule> modules) {
        for (final IErlModule module : modules) {
            final String moduleName = module.getModuleName();
            if (moduleName.startsWith(prefix)) {
                if (!names.contains(moduleName)) {
                    result.add(moduleName);
                    names.add(moduleName);
                }
            }
        }
    }

    public static String[] getPredefinedMacroNames() {
        return new String[] { "MODULE", "LINE", "FILE" };
    }

    public static ISourceRange findVariable(final IRpcCallSite backend,
            final ISourceRange range, final String variableName,
            final String elementText) throws OtpErlangRangeException {
        final OtpErlangTuple res2 = ErlideOpen.findFirstVar(backend,
                variableName, elementText);
        if (res2 != null) {
            final int relativePos = ((OtpErlangLong) res2.elementAt(0))
                    .intValue() - 1;
            final int length = ((OtpErlangLong) res2.elementAt(1)).intValue();
            final int start = relativePos + range.getOffset();
            return new SourceRange(start, length);
        }
        return range;
    }

    public static boolean isOtpModule(final IErlModule module) {
        IParent parent = module.getParent();
        while (parent instanceof IErlExternal) {
            final IErlExternal external = (IErlExternal) parent;
            if (external.isOTP()) {
                return true;
            }
            parent = external.getParent();
        }
        return false;
    }

    public static IErlElement findInclude(final IErlModule module,
            final IErlProject project, final OpenResult res,
            final IErlModel model) throws CoreException, BackendException {
        if (module != null) {
            final IErlModule include = model.findIncludeFromModule(module,
                    res.getName(), res.getPath(),
                    IErlModel.Scope.REFERENCED_PROJECTS);
            if (include != null) {
                return include;
            }
        } else if (project != null) {
            final IErlModule include = model.findIncludeFromProject(project,
                    res.getName(), res.getPath(),
                    IErlModel.Scope.REFERENCED_PROJECTS);
            if (include != null) {
                return include;
            }
        }
        return null;
    }

}
