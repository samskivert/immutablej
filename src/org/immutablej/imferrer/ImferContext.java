//
// $Id$

package org.immutablej.imferrer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;

/**
 * Tracks information needed by the imferrer.
 */
public class ImferContext
{
    /** The symbols in scope in this context. */ 
    public Scope scope = null;

    /** Maintains a mapping from symbol to AST node. */
    public Map<Symbol, JCVariableDecl> symToDecl = new HashMap<Symbol, JCVariableDecl>();

    /** The set of all symbols for which mutability was inferred. */
    public Set<Symbol> mutable = new HashSet<Symbol>();

    /**
     * Duplicates this context with the specified new scope.
     */
    public ImferContext dup (Scope scope)
    {
        ImferContext ctx = new ImferContext();
        ctx.scope = scope;
        ctx.symToDecl = symToDecl;
        ctx.mutable = mutable;
        return ctx;
    }
}
