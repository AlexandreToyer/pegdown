package org.pegdown;

import org.parboiled.trees.MutableTreeNodeImpl;

public class AstNode extends MutableTreeNodeImpl<AstNode> implements AstNodeType {

    public int type;
    public String text;

    public AstNode setType(int type) {
        this.type = type;
        return this;
    }

    public AstNode setText(String text) {
        this.text = text;
        return this;
    }

    public boolean addText(String text) {
        this.text += text;
        return true;
    }

    @Override
    public String toString() {
        return text == null ? TYPE_NAMES[type] : TYPE_NAMES[type] + ": \"" + text + '"';
    }
}
