//
// $Id$

package org.immutablej.imferrer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

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
import com.sun.tools.javac.util.List;
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
        // in theory this shouldn't happen, but it's possible that we'll get run on dependent
        // classes loaded from librarys; in that case we'll want to nix the below warning
        if (tree.sourcefile == null) {
            System.err.println("Skipping compilation unit for which we lack source: " + tree);
            return;
        }

        // this is a bit of a hack but there's no other good way to get our hands on the path to
        // the original source file
        File file = new File(tree.sourcefile.toString());
        if (!file.exists()) {
            System.err.println("Skipping compilation unit with strange sourcefile: " +
                               tree.sourcefile);
            return;
        }

        System.out.println("*** Processing " + tree.sourcefile);
        Env<ImferContext> oenv = _env;
        try {
            _env = new Env<ImferContext>(tree, new ImferContext());
            _env.toplevel = tree;
            _env.info.scope = tree.namedImportScope;

            // do our mutability inference
            tree.accept(this);

            // now add @var to all inferred mutable symbols
            Patcher patcher = new Patcher();
            int varsAdded = 0;
            Set<JCModifiers> seen = new HashSet<JCModifiers>();
            for (Symbol sym : _env.info.mutable) {
                JCVariableDecl decl = _env.info.symToDecl.get(sym);
                if (decl == null) {
                    System.err.println("Yikes! Missing declaration for symbol " + sym);
                    continue;
                }
                // avoid adding @var to source that already had @var and avoid repeatedly adding it
                // for variables that share modifiers (i.e. @var int foo = 1, bar = 2, baz = 0)
                if (!haveVarAnnotation(decl.mods.annotations) && !seen.contains(decl.mods)) {
                    seen.add(decl.mods);
                    System.err.println("Adding @var to " + decl);
                    patcher.insert(TreeInfo.getStartPos(decl.vartype), "@var ");
                    varsAdded++;
                }
                // TODO: if this decl contains a needless final modifier, remove it
            }

            // if we added @var annotations, make sure it's imported
            if (varsAdded > 0 && !haveVarImport(tree)) {
                String text = "import " + FQ_ANNOTATION + ";\n";
                int fpos = findFirstImportPos(tree);
                // if we found no imports then put our import after the package declaration
                if (fpos == Integer.MAX_VALUE) {
                    fpos = TreeInfo.getStartPos(tree.pid) + tree.pid.toString().length() +
                        1 /* semicolon: this will break if they have whitespace between the package
                           * name and the semicolon... sigh  */ +
                        System.getProperty("line.separator").length();
                    text = "\n" + text;
                } else {
                    text = text + "\n";
                }
                patcher.insert(fpos, text);
            }

            try {
                StringWriter temp = new StringWriter();
                patcher.apply(file, temp);
                FileWriter output = new FileWriter(file);
                output.write(temp.toString());
                output.close();
            } catch (IOException ioe) {
                System.err.println("Failed to patch original source [file=" + file + "].");
                ioe.printStackTrace(System.err);
            }

        } finally {
            _env = oenv;
        }
    }

    @Override public void visitClassDef (JCClassDecl tree) {
        System.err.println("Entering class '" + tree.name + "' (" + tree.sym + ")");

        // if we're visiting an anonymous inner class, we have to create a bogus scope as javac
        // does not Enter anonymous inner classes during the normal Enter phase
        Scope nscope;
        if (tree.sym == null || tree.sym.members_field == null) {
            if (_env.enclMethod != null) {
                nscope = new Scope(new ClassSymbol(0, tree.name, _env.enclMethod.sym));
            } else {
                nscope = new Scope(new ClassSymbol(0, tree.name, _env.enclClass.sym));
            }
        } else {
            nscope = tree.sym.members_field.dupUnshared();
        }

        // note the environment of the class we're processing
        Env<ImferContext> oenv = _env;
        _env = _env.dup(tree, oenv.info.dup(nscope));
        _env.enclClass = tree;
        _env.outer = oenv;
        super.visitClassDef(tree);
        _env = oenv;
    }

    @Override public void visitMethodDef (JCMethodDecl tree) {
        System.out.println("Entering method '" + tree.name + "'");

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

        // symbols for member-level variables and formal parameters are already created, we need to
        // create symbols for local variable declarations
        VarSymbol sym = tree.sym;
        if (sym == null) {
            // create a symbol for this variable which we'll use later to determine where we need
            // to insert @var
            sym = new VarSymbol(0, tree.name, null, _env.info.scope.owner);
            sym.pos = tree.pos;
        }

        // if we're inside a method, we need to enter all encountered symbols into scope
        if (_env.enclMethod != null) {
            _env.info.scope.enter(sym);
        }

        // no matter where we are, we need to update our mapping from symbol to AST node
        _env.info.symToDecl.put(sym, tree);

        // if this is a public or protected class member, we need to mark it mutable (unless it's
        // already marked final) because we can't know determine if it's ever mutated
        if (isNonPrivateMember(sym) && (sym.flags() & Flags.FINAL) == 0) {
            System.out.println("Need to make non-private member mutable " + sym);
            _env.info.mutable.add(sym);
        }
    }

    @Override public void visitUnary (JCUnary tree) {
        super.visitUnary(tree);

        System.out.println("Look ma, unop " + tree);

        // ++ and friends mutate
        if (UNOPS.contains(tree.tag) /* getTag() in 1.7 */ ) {
            noteMutable(tree.arg);
        }
    }

    @Override public void visitAssignop (JCAssignOp tree) {
        super.visitAssignop(tree);

        System.out.println("Look ma, assignop " + tree);

        // op= mutates its lhs
        noteMutable(tree.lhs);
    }

    @Override public void visitAssign (JCAssign tree) {
        super.visitAssign(tree);

        System.out.println("Look ma, assign " + tree);

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
                _env.info.mutable.add(vsym);
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

    protected static boolean haveVarAnnotation (List<JCAnnotation> anns)
    {
        if (anns.isEmpty()) {
            return false;
        } else {
            return anns.head.annotationType.toString().equals("var") ||
                haveVarAnnotation(anns.tail);
        }
    }

    protected static boolean haveVarImport (JCCompilationUnit tree)
    {
        final boolean[] found = new boolean[1];
        tree.accept(new TreeScanner() {
            public void visitImport (JCImport tree) {
                if (!tree.staticImport && tree.qualid.toString().equals(FQ_ANNOTATION)) {
                    found[0] = true;
                }
            }
        });
        return found[0];
    }

    protected static int findFirstImportPos (JCCompilationUnit tree)
    {
        final int[] pos = new int[] { Integer.MAX_VALUE };
        tree.accept(new TreeScanner() {
            public void visitImport (JCImport tree) {
                pos[0] = Math.min(TreeInfo.getStartPos(tree), pos[0]);
            }
        });
        return pos[0];
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

    protected static final Context.Key<Imfer> IMFER_KEY = new Context.Key<Imfer>();

    protected static final String FQ_ANNOTATION = "org.immutablej.var";
}
