//
// $Id$

package org.immutablej.imferrer;

import java.util.Set;

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

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;

/**
 * Infers mutability for method parameters, local variables and private fields. Mutability of
 * public and protected fields cannot be inferred via a non-global analysis.
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
                Diagnostic.Kind.WARNING, "Imferrer requires javac v1.6.");
            return;
        }

        Context ctx = ((JavacProcessingEnvironment)procenv).getContext();
        _trees = Trees.instance(procenv);
        _imfer = Imfer.instance(ctx);
        _procenv = procenv;

//         procenv.getMessager().printMessage(
//             Diagnostic.Kind.NOTE, "Imferrer running [vers=" + procenv.getSourceVersion() + "]");
    }

    @Override // from AbstractProcessor
    public boolean process (Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        if (_trees == null) {
            return false;
        }
        for (Element elem : roundEnv.getRootElements()) {
            _imfer.imfer(toUnit(elem));
        }
        return true; // we won't be run in conjunction with other annotation processors so we can
                     // safely claim to have handled everything
    }

    protected JCCompilationUnit toUnit (Element element)
    {
        TreePath path = _trees.getPath(element);
        return (path == null) ? null : (JCCompilationUnit)path.getCompilationUnit();
    }

    protected ProcessingEnvironment _procenv;
    protected Trees _trees;
    protected Imfer _imfer;
}
