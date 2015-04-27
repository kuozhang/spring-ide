/*******************************************************************************
 * Copyright (c) 2014 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.yaml.editor.completions;

import static org.springframework.ide.eclipse.boot.properties.editor.util.TypeUtil.formatJavaType;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.text.IJavaColorConstants;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension3;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension4;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension5;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.ICompletionProposalSorter;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.TextStyle;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.properties.editor.DocumentContextFinder;
import org.springframework.ide.eclipse.boot.properties.editor.FuzzyMap.Match;
import org.springframework.ide.eclipse.boot.properties.editor.PropertyInfo;
import org.springframework.ide.eclipse.boot.properties.editor.SpringPropertiesInformationControlCreator;
import org.springframework.ide.eclipse.boot.properties.editor.SpringPropertyHoverInfo;
import org.springframework.ide.eclipse.boot.properties.editor.util.Type;
import org.springframework.ide.eclipse.boot.properties.editor.util.TypedProperty;
import org.springframework.ide.eclipse.yaml.editor.ast.path.YamlPath;

public class PropertyCompletionFactory {

	public ICompletionProposal property(IDocument doc, int offset, ProposalApplier applier, Match<PropertyInfo> prop) {
		return new PropertyProposal(doc, offset, applier, prop);
	}

	public ICompletionProposal valueProposal(String value, Type type, int sortingOrder, ProposalApplier applier) {
		return simpleProposal(value, sortingOrder, applier);
	}

	public ICompletionProposal beanProperty(YamlDocument doc, int offset, YamlPath contextPath, TypedProperty p, int sortingOrder, ProposalApplier applier) {
		return simpleProposal(p.getName(), sortingOrder, applier);
	}


	//TODO: move to a place where this can be shared with SpringProperties completion engine
	// and use it instead of almost duplicated code.

	private ICompletionProposal simpleProposal(String name, int sortingOrder, ProposalApplier applier) {
		return new SimpleProposal(name, sortingOrder, applier);
	}


	private static class SimpleProposal implements ICompletionProposal {

		private String value;
		private int sortingOrder;
		private ProposalApplier applier;

		public SimpleProposal(String value, int sortingOrder, ProposalApplier applier) {
			this.value = value;
			this.sortingOrder = sortingOrder;
			this.applier = applier;
		}

		@Override
		public void apply(IDocument doc) {
			try {
				applier.apply(doc);
			} catch (Exception e) {
				BootActivator.log(e);
			}
		}

		@Override
		public Point getSelection(IDocument doc) {
			try {
				return applier.getSelection(doc);
			} catch (Exception e) {
				BootActivator.log(e);
			}
			return null;
		}

		@Override
		public String getAdditionalProposalInfo() {
			//TODO: display JavaDoc info for the type if available.
			return null;
		}

		@Override
		public String getDisplayString() {
			return value;
		}

		@Override
		public Image getImage() {
			return null;
		}

		@Override
		public IContextInformation getContextInformation() {
			return null;
		}

	}

	/**
	 * A sorter suitable for sorting proposals created by this factory
	 */
	public static final ICompletionProposalSorter SORTER = new ICompletionProposalSorter() {
		public int compare(ICompletionProposal p1, ICompletionProposal p2) {
			if (p1 instanceof PropertyProposal && p2 instanceof PropertyProposal) {
				double s1 = ((PropertyProposal)p1).match.score;
				double s2 = ((PropertyProposal)p2).match.score;
				if (s1==s2) {
					String name1 = ((PropertyProposal)p1).match.data.getId();
					String name2 = ((PropertyProposal)p2).match.data.getId();
					return name1.compareTo(name2);
				} else {
					return Double.compare(s2, s1);
				}
			} else if (p1 instanceof SimpleProposal && p2 instanceof SimpleProposal) {
				int order1 = ((SimpleProposal)p1).sortingOrder;
				int order2 = ((SimpleProposal)p2).sortingOrder;
				return Integer.valueOf(order1).compareTo(Integer.valueOf(order2));
			}
			return 0;
		}
	};

	private DocumentContextFinder documentContextFinder;

	private Styler JAVA_STRING_COLOR = new Styler() {
		@Override
		public void applyStyles(TextStyle textStyle) {
			textStyle.foreground = JavaUI.getColorManager().getColor(IJavaColorConstants.JAVA_STRING);
		}
	};
	private Styler JAVA_KEYWORD_COLOR = new Styler() {
		@Override
		public void applyStyles(TextStyle textStyle) {
			textStyle.foreground = JavaUI.getColorManager().getColor(IJavaColorConstants.JAVA_KEYWORD);
		}
	};
	private Styler JAVA_OPERATOR_COLOR = new Styler() {
		@Override
		public void applyStyles(TextStyle textStyle) {
			textStyle.foreground = JavaUI.getColorManager().getColor(IJavaColorConstants.JAVA_OPERATOR);
		}
	};


	public PropertyCompletionFactory(DocumentContextFinder documentContextFinder) {
		this.documentContextFinder = documentContextFinder;
	}

	private class PropertyProposal implements ICompletionProposal, ICompletionProposalExtension3,
	ICompletionProposalExtension4, ICompletionProposalExtension5, ICompletionProposalExtension6
	{

//		private final String fPrefix;
		private final int fOffset;
		private Match<PropertyInfo> match;
		private IDocument fDoc;
		private ProposalApplier proposalApplier;

		public PropertyProposal(IDocument doc, int offset, ProposalApplier applier,  Match<PropertyInfo> match) {
//			fPrefix= prefix;
			this.fOffset= offset;
			this.match = match;
			this.proposalApplier = applier;
			this.fDoc = doc;
		}

		public Point getSelection(IDocument document) {
			try {
				return proposalApplier.getSelection(document);
			} catch (Exception e) {
				BootActivator.log(e);
				return null;
			}
		}

		public String getAdditionalProposalInfo() {
			PropertyInfo data = match.data;
			return SpringPropertyHoverInfo.getHtmlHoverText(data);
		}

		@Override
		public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
			return new SpringPropertyHoverInfo(documentContextFinder.getJavaProject(fDoc), match.data);
		}

		public String getDisplayString() {
			StyledString styledText = getStyledDisplayString();
			return styledText.getString();
		}

		public Image getImage() {
			return null;
		}

		public IContextInformation getContextInformation() {
			return null;
		}

//		public boolean isValidFor(IDocument document, int offset) {
//			return validate(document, offset, null);
//		}

//		public char[] getTriggerCharacters() {
//			return null;
//		}
//
//		public int getContextInformationPosition() {
//			return 0;
//		}

//		public void apply(IDocument document) {
//			apply(document, '\0', fOffset);
//		}

//		public void apply(ITextViewer viewer, char trigger, int stateMask, int offset) {
//			apply(viewer.getDocument(), trigger, offset);
//		}

//		public void apply(IDocument doc, char trigger, int offset) {
//			try {
//				proposalApplier.apply(doc);
//			} catch (Exception e) {
//				BootActivator.log(e);
//			}
//		}

//		public void selected(ITextViewer viewer, boolean smartToggle) {
//		}
//
//		public void unselected(ITextViewer viewer) {
//		}
//
//		public boolean validate(IDocument document, int offset, DocumentEvent event) {
//			try {
//				int prefixStart= fOffset - fPrefix.length();
//				String newPrefix = document.get(prefixStart, offset-prefixStart);
//				double newScore = FuzzyMap.match(newPrefix, match.data.getId());
//				if (newScore!=0.0) {
//					match.score = newScore;
//					return true;
//				}
//			} catch (BadLocationException x) {
//			}
//			return false;
//		}

//		public IInformationControlCreator getInformationControlCreator() {
//			return null;
//		}

//		public CharSequence getPrefixCompletionText(IDocument document, int completionOffset) {
//			return match.data.getId();
//		}

//		public int getPrefixCompletionStart(IDocument document, int completionOffset) {
//			return fOffset - fPrefix.length();
//		}

		public boolean isAutoInsertable() {
			return true;
		}

//		private String getCompletion() {
//			StringBuilder completion = new StringBuilder(match.data.getId());
//			Type type = TypeParser.parse(match.data.getType());
//			completion.append(propertyCompletionPostfix(type));
//			return completion.toString();
//		}

		@Override
		public StyledString getStyledDisplayString() {
			StyledString result = new StyledString();
			result.append(match.data.getId());
			String defaultValue = SpringPropertyHoverInfo.formatDefaultValue(match.data.getDefaultValue());
			if (defaultValue!=null) {
				result.append("=", JAVA_OPERATOR_COLOR);
				result.append(defaultValue, JAVA_STRING_COLOR);
			}
			String type = formatJavaType(match.data.getType());
			if (type!=null) {
				result.append(" : ");
				result.append(type, JAVA_KEYWORD_COLOR);
			}
			String description = getShortDescription();
			if (description!=null && !"".equals(description.trim())) {
				result.append(" ");
				result.append(description.trim(), StyledString.DECORATIONS_STYLER);
			}
			return result;
		}

		private String getShortDescription() {
			String description = match.data.getDescription();
			if (description!=null) {
				int dotPos = description.indexOf('.');
				if (dotPos>=0) {
					description = description.substring(0, dotPos+1);
				}
				description = description.replaceAll("\\p{Cntrl}", ""); //mostly here to remove line breaks, these mess with the layout in the popup control.
				return description;
			}
			return null;
		}


		@Override
		public String toString() {
			return match.data.getId();
		}

		@Override
		public void apply(IDocument document) {
			try {
				proposalApplier.apply(document);
			} catch (Exception e) {
				BootActivator.log(e);
			}
		}

		@Override
		public IInformationControlCreator getInformationControlCreator() {
			return new SpringPropertiesInformationControlCreator("F2 for focus");
		}

		@Override
		public CharSequence getPrefixCompletionText(IDocument document, int completionOffset) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int getPrefixCompletionStart(IDocument document,
				int completionOffset) {
			return completionOffset;
		}

	}

}