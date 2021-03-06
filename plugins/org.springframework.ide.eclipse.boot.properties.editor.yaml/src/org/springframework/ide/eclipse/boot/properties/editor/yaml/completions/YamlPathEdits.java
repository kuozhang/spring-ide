/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.properties.editor.yaml.completions;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IRegion;
import org.springframework.ide.eclipse.boot.properties.editor.completions.DocumentEdits;
import org.springframework.ide.eclipse.boot.properties.editor.yaml.path.YamlPath;
import org.springframework.ide.eclipse.boot.properties.editor.yaml.path.YamlPathSegment;
import org.springframework.ide.eclipse.boot.properties.editor.yaml.path.YamlPathSegment.YamlPathSegmentType;
import org.springframework.ide.eclipse.boot.properties.editor.yaml.structure.YamlStructureParser.SChildBearingNode;
import org.springframework.ide.eclipse.boot.properties.editor.yaml.structure.YamlStructureParser.SKeyNode;
import org.springframework.ide.eclipse.boot.properties.editor.yaml.structure.YamlStructureParser.SNode;
import org.springframework.ide.eclipse.boot.properties.editor.yaml.structure.YamlStructureParser.SNodeType;
import org.springframework.ide.eclipse.boot.properties.editor.yaml.structure.YamlStructureParser.SRootNode;

/**
 * Helper class that provides methods for creating the edits in a YamlDocument that
 * insert new 'property paths' into the document.
 *
 * @author Kris De Volder
 */
public class YamlPathEdits extends DocumentEdits {

	private YamlDocument doc;
	private IndentUtil indentUtil;

	public YamlPathEdits(YamlDocument doc) {
		super(doc.getDocument());
		this.doc = doc;
		this.indentUtil = new IndentUtil(doc);
	}

	/**
	 * Create the necessary edits to ensure that a given property
	 * path exists, placing cursor right after that the right place to start typing
	 * the property value.
	 */
	public void createPath(YamlPath path, String appendText) throws Exception {
		SRootNode root = doc.getStructure();
		createPath(root, path, appendText);
	}

	/**
	 * Like createPath, but path is created relative to a parent node instead of
	 * at the document root.
	 */
	public void createPath(SChildBearingNode node, YamlPath path, String appendText) throws Exception {
		if (!path.isEmpty()) {
			YamlPathSegment s = path.getSegment(0);
			if (s.getType()==YamlPathSegmentType.VAL_AT_KEY) {
				String key = s.toPropString();
				SKeyNode existing = findChildForKey(node, key);
				if (existing==null) {
					createNewPath(node, path, appendText);
				} else {
					createPath(existing, path.tail(), appendText);
				}
			}
		} else {
			//whole path already exists. Just try to move cursor somewhere
			// sensible in the existing tail-end-node of the path.
			SNode child = node.getFirstRealChild();
			if (child!=null) {
				moveCursorTo(child.getStart());
			} else if (node.getNodeType()==SNodeType.KEY) {
				SKeyNode keyNode = (SKeyNode) node;
				int colonOffset = keyNode.getColonOffset();
				char c = doc.getChar(colonOffset+1);
				if (c==' ') {
					moveCursorTo(colonOffset+2); //cursor after the ": "
				} else {
					moveCursorTo(colonOffset+1); //cursor after the ":"
				}
			}
		}
	}

	private void createNewPath(SChildBearingNode parent, YamlPath path, String appendText) throws Exception {
		int indent = getChildIndent(parent);
		int insertionPoint = getNewPathInsertionOffset(parent);
		boolean startOnNewLine = true;
		insert(insertionPoint, createPathInsertionText(path, indent, startOnNewLine, appendText));
	}

	protected String createPathInsertionText(YamlPath path, int indent, boolean startOnNewLine, String appendText) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < path.size(); i++) {
			if (startOnNewLine||i>0) {
				indentUtil.addNewlineWithIndent(indent, buf);
			}
			String key = path.getSegment(i).toPropString();
			buf.append(key);
			buf.append(":");
			indent += IndentUtil.INDENT_BY;
		}
		buf.append(indentUtil.applyIndentation(appendText, indent));
		return buf.toString();
	}

	private int getChildIndent(SNode parent) {
		if (parent.getNodeType()==SNodeType.ROOT) {
			return parent.getIndent();
		} else {
			return parent.getIndent()+IndentUtil.INDENT_BY;
		}
	}

	private int getNewPathInsertionOffset(SChildBearingNode parent) throws Exception {
		int insertAfterLine = doc.getLineOfOffset(parent.getTreeEnd());
		while (insertAfterLine>=0 && doc.getLineIndentation(insertAfterLine)==-1) {
			insertAfterLine--;
		}
		if (insertAfterLine<0) {
			//This code is probably 'dead' because:
			//   - it can only occur if all lines in the 'parent' are empty
			//   - if parent is any other node than SRootNode then it must have at least one
			//     non-emtpy line
			//  => parent must be SRootNode and only contain comment or empty lines
			//  But in that case we will never need to compute a 'new path insertion offset'
			//  since we will always be in the case where completions are to be inserted
			//  in place (i.e. at the current cursor).
			return 0; //insert at beginning of document
		} else {
			IRegion r = doc.getLineInformation(insertAfterLine);
			return r.getOffset() + r.getLength();
		}
	}

	private SKeyNode findChildForKey(SChildBearingNode node, String key) throws Exception {
		for (SNode c : node.getChildren()) {
			if (c.getNodeType()==SNodeType.KEY) {
				String nodeKey = ((SKeyNode)c).getKey();
				//TODO: relax matching camel-case -> hyphens
				if (key.equals(nodeKey)) {
					return (SKeyNode)c;
				}
			}
		}
		return null;
	}

	public void createPathInPlace(SNode contextNode, YamlPath relativePath, int insertionPoint, String appendText) throws Exception {
		int indent = getChildIndent(contextNode);
		insert(insertionPoint, createPathInsertionText(relativePath, indent, needNewline(contextNode, insertionPoint), appendText));
	}

	private boolean needNewline(SNode contextNode, int insertionPoint) throws Exception {
		if (contextNode.getNodeType()==SNodeType.SEQ) {
			// after a '- ' its okay to put key on same line
			return false;
		} else {
			return lineHasTextBefore(insertionPoint);
		}
	}

	private boolean lineHasTextBefore(int insertionPoint) throws Exception {
		String textBefore = doc.getLineTextBefore(insertionPoint);
		return !textBefore.trim().isEmpty();
	}

}
