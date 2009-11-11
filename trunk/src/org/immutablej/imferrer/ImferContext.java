//
// $Id$

package org.immutablej.imferrer;

import com.sun.tools.javac.code.Scope;

/**
 * Tracks information needed by the imferrer.
 */
public class ImferContext
{
    /** The symbols in scope in this context. */ 
    public Scope scope = null;

    /**
     * Duplicates this context with the specified new scope.
     */
    public ImferContext dup (Scope scope)
    {
        ImferContext ctx = new ImferContext();
        ctx.scope = scope;
        return ctx;
    }
}
