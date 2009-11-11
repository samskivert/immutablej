//
// $Id$

package org.immutablej.immuter;

import java.util.Set;
import java.util.HashSet;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

/**
 * The main entry point for the immuting processor.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions({ Processor.HANDLE_STAR })
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class Processor extends AbstractProcessor
{
    @Override // from AbstractProcessor
    public void init (ProcessingEnvironment procenv)
    {
        super.init(procenv);

        if (!(procenv instanceof JavacProcessingEnvironment)) {
            procenv.getMessager().printMessage(
                Diagnostic.Kind.WARNING, "Immuter requires javac v1.6.");
            return;
        }

        Context ctx = ((JavacProcessingEnvironment)procenv).getContext();
        _trees = Trees.instance(procenv);
        _procenv = procenv;

        // note our options
        _handleStar = "true".equalsIgnoreCase(procenv.getOptions().get(HANDLE_STAR));

        procenv.getMessager().printMessage(
            Diagnostic.Kind.NOTE, "Immuter running [vers=" + procenv.getSourceVersion() + "]");
    }

    @Override // from AbstractProcessor
    public boolean process (Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        if (_trees == null) {
            return false;
        }

        for (Element elem : roundEnv.getRootElements()) {
            final JCCompilationUnit unit = toUnit(elem);

            // we only want to operate on files being compiled from source; if they're already
            // classfiles then we've already run or we're looking at a library class
            if (unit.sourcefile.getKind() != JavaFileObject.Kind.SOURCE) {
                System.err.println("Skipping non-source-file " + unit.sourcefile);
                continue;
            }

            System.err.println("Processing " + unit.sourcefile);
            unit.accept(new TreeTranslator() {
                public void visitVarDef (JCVariableDecl tree) {
                    super.visitVarDef(tree);

                    // if this variable declaration's modifiers have already been processed
                    // (variables can share modifiers, ie. public @var int foo, bar), then don't
                    // repeat process this declaration
                    if (_seen.contains(tree.mods)) {
                        return;
                    }
                    _seen.add(tree.mods);

                    // note the number of annotations on this var
                    int ocount = tree.mods.annotations.size();

                    // remove the @var annotation if we see it
                    tree.mods.annotations = removeVar(tree.mods.annotations);

                    // if we didn't remove anything, then make the variable final
                    if (tree.mods.annotations.size() == ocount) {
                        tree.mods.flags |= Flags.FINAL;

                    // check for retardation
                    } else if ((tree.mods.flags & Flags.FINAL) != 0) {
                            _procenv.getMessager().printMessage(
                                Diagnostic.Kind.WARNING,
                                "@var annotated variable also marked final: " + tree,
                                // TODO: this should work but it doesn't, sigh
                                _trees.getElement(TreePath.getPath(unit, tree)));
                    }
                }

                protected Set<JCModifiers> _seen = new HashSet<JCModifiers>();
            });
        }

        // TODO: it would be nice if we could say that we handled @var but there seems to be no way
        // to say you accept "*" but then tell javac you handled some of the annotations you saw
        return _handleStar;
    }

    protected JCCompilationUnit toUnit (Element element)
    {
        TreePath path = _trees.getPath(element);
        return (path == null) ? null : (JCCompilationUnit)path.getCompilationUnit();
    }

    protected static List<JCAnnotation> removeVar (List<JCAnnotation> list)
    {
        if (list.isEmpty()) {
            return list;
        // note: I'd use Name.Table/Names here but then we'd have a 1.6 vs. 1.7 incompatibility
        } else if (list.head.annotationType.toString().equals("var")) {
            return list.tail;
        } else {
            return removeVar(list.tail).prepend(list.head);
        }
    }

    protected ProcessingEnvironment _procenv;
    protected Trees _trees;
    protected boolean _handleStar;

    protected static final String HANDLE_STAR = "org.immutablej.handle_star";
}
