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
@SupportedAnnotationTypes("com.samskivert.elbatum.var")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class Processor extends AbstractProcessor
{
    @Override // from AbstractProcessor
    public void init (ProcessingEnvironment procenv)
    {
        super.init(procenv);

        if (!(procenv instanceof JavacProcessingEnvironment)) {
            procenv.getMessager().printMessage(
                Diagnostic.Kind.WARNING, "Detyper requires javac v1.6.");
            return;
        }

        Context ctx = ((JavacProcessingEnvironment)procenv).getContext();
        _trees = Trees.instance(procenv);

        System.err.println("Elbatum running [vers=" + procenv.getSourceVersion() + "]");
    }

    @Override // from AbstractProcessor
    public boolean process (Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        if (_trees == null) {
            return false;
        }

        for (Element elem : roundEnv.getRootElements()) {
            JCCompilationUnit unit = toUnit(elem);

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
                    }
                }
            });

//             RT.debug("Root elem " + elem, "unit", unit.getClass().getSimpleName(),
//                      "sym.mems", ASTUtil.expand(unit.packge.members_field.elems.sym));
        }
        return true;
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

    protected Trees _trees;
}
