/*******************************************************************************
 * Copyright (c) 2004 Eric Merritt and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eric Merritt
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.ui.editors.erl;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.reconciler.MonoReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;
import org.eclipse.ui.texteditor.ITextEditor;
import org.erlide.core.erlang.IErlModule;
import org.erlide.core.erlang.IErlScanner;
import org.erlide.ui.editors.util.HTMLTextPresenter;
import org.erlide.ui.util.ErlModelUtils;
import org.erlide.ui.util.IColorManager;

/**
 * The editor configurator
 * 
 * 
 * @author Eric Merritt [cyberlync at gmail dot com]
 */
public class EditorConfiguration extends TextSourceViewerConfiguration {

	private ErlangEditor editor;

	private ITextDoubleClickStrategy doubleClickStrategy;

	// private IErlScanner fScanner;

	private ErlHighlightScanner fHighlightScanner;

	private IColorManager colorManager;

	private ErlPairMatcher fBracketMatcher;

	/**
	 * Default configuration constructor
	 * 
	 * @param store
	 * 
	 * @param editor
	 * 
	 * @param lcolorManager
	 *            the color manager
	 */
	public EditorConfiguration(IPreferenceStore store, ErlangEditor leditor,
			IColorManager lcolorManager) {
		super(store);
		this.colorManager = lcolorManager;
		this.editor = leditor;
	}

	/**
	 * The standard content types
	 * 
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getConfiguredContentTypes(org.eclipse.jface.text.source.ISourceViewer)
	 */
	@Override
	public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
		return IErlangPartitions.LEGAL_PARTITIONS;
	}

	/**
	 * The double click strategy
	 * 
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getDoubleClickStrategy(org.eclipse.jface.text.source.ISourceViewer,
	 *      java.lang.String)
	 */
	@Override
	public ITextDoubleClickStrategy getDoubleClickStrategy(
			ISourceViewer sourceViewer, String contentType) {
		if (doubleClickStrategy == null) {
			// doubleClickStrategy = new
			// ErlDoubleClickSelector(getBracketMatcher());
			doubleClickStrategy = new DoubleClickStrategy(getBracketMatcher());
		}
		return doubleClickStrategy;
	}

	/**
	 * Creates and returns the fHighlightScanner
	 * 
	 * @return the highlighting fHighlightScanner
	 */
	protected ErlHighlightScanner getHighlightScanner() {
		if (fHighlightScanner == null) {
			fHighlightScanner = new ErlHighlightScanner(colorManager);
		}
		return fHighlightScanner;
	}

	public ErlPairMatcher getBracketMatcher() {
		if (fBracketMatcher == null) {
			final IErlScanner mod = ErlModelUtils.getScanner(editor);
			fBracketMatcher = new ErlPairMatcher(mod);
		}
		return fBracketMatcher;
	}

	/**
	 * Creates the reconciler
	 * 
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getPresentationReconciler(org.eclipse.jface.text.source.ISourceViewer)
	 */
	@Override
	public IPresentationReconciler getPresentationReconciler(
			ISourceViewer sourceViewer) {
		final PresentationReconciler reconciler = new PresentationReconciler();

		final ErlHighlightScanner scan = getHighlightScanner();
		if (scan != null) {
			final DefaultDamagerRepairer dr = new ErlDamagerRepairer(scan);
			reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
			reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);
		}
		return reconciler;
	}

	/*
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getAutoEditStrategies(org.eclipse.jface.text.source.ISourceViewer,
	 *      java.lang.String)
	 */
	@Override
	public IAutoEditStrategy[] getAutoEditStrategies(
			ISourceViewer sourceViewer, String contentType) {
		// final String partitioning =
		// getConfiguredDocumentPartitioning(sourceViewer);
		return new IAutoEditStrategy[] { new AutoIndentStrategy() };
	}

	@Override
	public ITextHover getTextHover(ISourceViewer sourceViewer,
			String contentType) {
		final IErlModule module = ErlModelUtils.getModule(editor);
		if (module != null) {
			return new ErlTextHover(module);
		} else {
			return null;
		}
	}

	/**
	 * Returns the editor in which the configured viewer(s) will reside.
	 * 
	 * @return the enclosing editor
	 */
	protected ITextEditor getEditor() {
		return editor;
	}

	@Override
	public String getConfiguredDocumentPartitioning(ISourceViewer sourceViewer) {
		return IErlangPartitions.ERLANG_PARTITIONING;
	}

	@Override
	public IReconciler getReconciler(ISourceViewer sourceViewer) {
		final ErlReconcilerStrategy strategy = new ErlReconcilerStrategy(editor);
		final MonoReconciler reconciler = new MonoReconciler(strategy, true);
		// reconciler.setIsIncrementalReconciler(false);
		reconciler.setProgressMonitor(new NullProgressMonitor());
		reconciler.setDelay(500);
		return reconciler;
	}

	@Override
	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
		if (getEditor() != null) {
			final ContentAssistant asst = new ContentAssistant();

			asst
					.setContentAssistProcessor(new ErlContentAssistProcessor(
							(ErlangEditor) getEditor()),
							IDocument.DEFAULT_CONTENT_TYPE);

			asst.enableAutoActivation(true);
			asst.setAutoActivationDelay(500);
			asst.enableAutoInsert(true);
			asst.enablePrefixCompletion(false);
			asst.setDocumentPartitioning(IErlangPartitions.ERLANG_PARTITIONING);

			asst
					.setProposalPopupOrientation(IContentAssistant.PROPOSAL_OVERLAY);
			asst
					.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE);
			asst
					.setInformationControlCreator(getInformationControlCreator(sourceViewer));

			return asst;
		}
		return null;
	}

	@Override
	public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
		return new ErlangAnnotationHover();
	}

	/*
	 * @see SourceViewerConfiguration#getInformationControlCreator(ISourceViewer)
	 * @since 2.0
	 */
	@Override
	public IInformationControlCreator getInformationControlCreator(
			ISourceViewer sourceViewer) {
		return new IInformationControlCreator() {

			public IInformationControl createInformationControl(Shell parent) {
				return new DefaultInformationControl(parent, SWT.NONE,
						new HTMLTextPresenter(true));
			}
		};
	}

	/**
	 * Returns the information presenter control creator. The creator is a
	 * factory creating the presenter controls for the given source viewer. This
	 * implementation always returns a creator for
	 * <code>DefaultInformationControl</code> instances.
	 * 
	 * @param sourceViewer
	 *            the source viewer to be configured by this configuration
	 * @return an information control creator
	 * @since 2.1
	 */
	/* NOT USED */
	/*
	 * private IInformationControlCreator getInformationPresenterControlCreator(
	 * ISourceViewer sourceViewer) { return new IInformationControlCreator() {
	 * 
	 * public IInformationControl createInformationControl(Shell parent) { final
	 * int shellStyle = SWT.RESIZE | SWT.TOOL; final int style = SWT.V_SCROLL |
	 * SWT.H_SCROLL; return new DefaultInformationControl(parent, shellStyle,
	 * style, new HTMLTextPresenter(false)); } }; }
	 */
}
