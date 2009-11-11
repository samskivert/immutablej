//
// $Id$

package org.immutablej.immuter;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

/**
 * The main entry point for the elbatum processor.
 */
@SupportedAnnotationTypes("*")
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

//         procenv.getMessager().printMessage(
//             Diagnostic.Kind.NOTE, "Immuter running [vers=" + procenv.getSourceVersion() + "]");
    }

    @Override // from AbstractProcessor
    public boolean process (Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        if (_trees == null) {
            return false;
        }

        for (Element elem : roundEnv.getRootElements()) {
            final JCCompilationUnit unit = toUnit(elem);

            unit.accept(new TreeTranslator() {
                public void visitVarDef (JCVariableDecl tree) {
                    super.visitVarDef(tree);

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
            });
        }

        // TODO: it would be nice if we could say that we handled @var but there seems to be no way
        // to say you accept "*" but then tell javac you handled some of the annotations you saw
        return false;
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
}
