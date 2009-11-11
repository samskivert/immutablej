//
// $Id$

package org.immutablej.imferrer;

import java.util.Set;
import java.util.HashSet;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;

/**
 * Performs the immutability inference process.
 */
public class Imfer extends TreeScanner
{
    /**
     * Returns the immutability inferrer.
     */
    public static Imfer instance (Context context)
    {
        Imfer instance = context.get(IMFER_KEY);
        if (instance == null) {
            instance = new Imfer(context);
        }
        return instance;
    }

    /**
     * Infers immutability on the supplied compilation unit.
     */
    public void imfer (JCCompilationUnit tree)
    {
        Env<ImferContext> oenv = _env;
        try {
            _env = new Env<ImferContext>(tree, new ImferContext());
            _env.toplevel = tree;
            _env.info.scope = tree.namedImportScope;
            tree.accept(this);
        } finally {
            _env = oenv;
        }
    }

    @Override public void visitClassDef (JCClassDecl tree) {
        System.err.println("Entering class '" + tree.name + "'");

        // if we're visiting an anonymous inner class, we have to create a bogus scope as javac
        // does not Enter anonymous inner classes during the normal Enter phase
        Scope nscope = (tree.sym != null) ? tree.sym.members_field.dupUnshared() :
            new Scope(new ClassSymbol(0, tree.name, _env.enclMethod.sym));

        // note the environment of the class we're processing
        Env<ImferContext> oenv = _env;
        _env = _env.dup(tree, oenv.info.dup(nscope));
        _env.enclClass = tree;
        _env.outer = oenv;
        super.visitClassDef(tree);
        _env = oenv;
    }

    @Override public void visitMethodDef (JCMethodDecl tree) {
        System.out.println("Entering method: " + tree);

        // create a local environment for this method definition
        Env<ImferContext> oenv = _env;
        _env = _env.dup(tree, oenv.info.dup(oenv.info.scope.dupUnshared()));
        _env.enclMethod = tree;
        _env.info.scope.owner = tree.sym;

        // now we can call super and translate our children
        super.visitMethodDef(tree);

        // restore our previous environment
        _env = oenv;
    }

    @Override public void visitVarDef (JCVariableDecl tree) {
        super.visitVarDef(tree);

        // var symbols for member-level variables are already entered, we just want to handle
        // formal parameters and local variable declarations
        VarSymbol sym = tree.sym;
        if (sym == null) {
            // create a symbol for this variable which we'll use later to determine where we need
            // to insert @var
            sym = new VarSymbol(0, tree.name, null, _env.info.scope.owner);
            sym.pos = tree.pos;
            _env.info.scope.enter(sym);
            System.out.println("Created var sym " + sym);
        }

        // TODO: if this vardef includes a final modifier (and it's not a public or protected
        // member), schedule it for removal
    }

    @Override public void visitUnary (JCUnary tree) {
        super.visitUnary(tree);

        // ++ and friends mutate
        if (UNOPS.contains(tree.tag) /* getTag() in 1.7 */ ) {
            noteMutable(tree.arg);
        }
    }

    @Override public void visitBinary (JCBinary tree) {
        super.visitBinary(tree);

        // += and friends mutate their lhs
        if (BINOPS.contains(tree.tag) /* getTag() in 1.7 */ ) {
            noteMutable(tree.lhs);
        }
    }

    @Override public void visitAssign (JCAssign tree) {
        super.visitAssign(tree);

        // = mutates its lhs
        noteMutable(tree.lhs);
    }

    protected Imfer (Context ctx)
    {
        ctx.put(IMFER_KEY, this);
    }

    protected void noteMutable (JCExpression tree)
    {
        // if the lhs is an identifier, then we may be mutating an in-scope variable
        Name vname = getVarName(tree);
        if (vname != null) {
            Symbol vsym = findVar(vname);
            if (vsym == null) {
                System.err.println("Thought we had a var? " + tree);
            } else if (isNonPrivateMember(vsym)) {
                System.err.println("Can't do anything with non-private member. " + tree);
            } else {
                System.err.println("Mark this var as mutable! " + tree);
            }
        }
    }

    protected Name getVarName (JCTree tree)
    {
        if (tree instanceof JCIdent) {
            return TreeInfo.name(tree);

        } else if (tree instanceof JCFieldAccess) {
            // if we're accessing an object member through the this reference, we want to treat
            // that as if we just referenced the member directly
            JCFieldAccess fa = (JCFieldAccess)tree;
            if (fa.selected.toString().equals("this")) {
                return fa.name;
            }
        }

        return null;
    }

    /**
     * Returns true if the supplied symbol is a non-private member of a class. We can't infer
     * anything for such variables because they may be mutated by non-local code.
     */
    protected boolean isNonPrivateMember (Symbol vsym)
    {
        return (vsym.owner instanceof ClassSymbol &&
                (vsym.flags() & (Flags.PUBLIC|Flags.PROTECTED)) != 0);
    }

    protected Symbol findVar (Name name)
    {
        for (Env<ImferContext> env = _env; env.outer != null; env = env.outer) {
            Symbol sym = lookup(env.info.scope, name, Kinds.VAR);
            if (sym != null) {
                return sym;
            }
        }
        return null;
    }

    protected static Symbol lookup (Scope scope, Name name, int kind)
    {
        for ( ; scope != Scope.emptyScope && scope != null; scope = scope.next) {
            Symbol sym = first(scope.lookup(name), kind);
            if (sym != null) {
                return sym;
            }
        }
        return null;
    }

    protected static Symbol first (Scope.Entry e, int kind)
    {
        for ( ;e != null && e.scope != null; e = e.next()) {
            if (e.sym.kind == kind) {
                return e.sym;
            }
        }
        return null;
    }

    protected Env<ImferContext> _env;

    /** The set of all mutating unary operators. */
    protected static final Set<Integer> UNOPS = new HashSet<Integer>();
    static {
        UNOPS.add(JCTree.PREINC);  // ++v
        UNOPS.add(JCTree.POSTINC); // v++
        UNOPS.add(JCTree.PREDEC);  // --v
        UNOPS.add(JCTree.POSTDEC); // v--
    }

    /** The set of binary operators that mutate their lhs. */
    protected static final Set<Integer> BINOPS = new HashSet<Integer>();
    static {
        BINOPS.add(JCTree.BITOR_ASG);  // |=
        BINOPS.add(JCTree.BITXOR_ASG); // ^=
        BINOPS.add(JCTree.BITAND_ASG); // &=
        BINOPS.add(JCTree.SL_ASG);     // <<=
        BINOPS.add(JCTree.SR_ASG);     // >>=
        BINOPS.add(JCTree.USR_ASG);    // >>>=
        BINOPS.add(JCTree.PLUS_ASG);   // +=
        BINOPS.add(JCTree.MINUS_ASG);  // -=
        BINOPS.add(JCTree.MUL_ASG);    // *=
        BINOPS.add(JCTree.DIV_ASG);    // /=
        BINOPS.add(JCTree.MOD_ASG);    // %=
    }

    protected static final Context.Key<Imfer> IMFER_KEY = new Context.Key<Imfer>();
}
