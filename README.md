Ever thought that final should be the default in Java? Well, you can very easily make that true
with this handy javac plugin. Just follow the simple instructions below to integrate this into your
build and reap the benefits of immutable by default.

## What does it do?

It's very simple. All variables are converted to *final* by default. If you want a non-final
variable, you have to annotate it with *@var*. Here's a simple example:

```java
import org.immutablej.var;

public class Foo {
    public static void main (String[] args) {
        // args = new String[] { "w00t!" }; // illegal!

        int foo = 1;
        // foo = 2; // illegal!

        @var int bar = 1;
        bar = 2; // legal

        // _one = 1; // illegal!
        _two = 2; // legal
    }

    void foo (@var int value, int constant) {
        value = 5; // legal
        // constant = 5; // illegal!
    }

    int _one = 0;
    @var int _two;
}
```

Note: you can put final on your variables manually if you like but it's redundant. If you define a
variable as `@var final type name` then we're going to assume you mean that you want it final and
leave it final, but we also emit a warning. Don't do that!

*Question* to anyone who has an opinion: do you think the annotation should be
`javax.annotation.var` instead of `org.immutablej.var`? This strikes me as something at least as
fundamental as @Nullable, so having a semi-standard annotation may be useful.

## How do I use it on my project?

It could hardly be simpler. Download
[immuter.jar](http://immutablej.googlecode.com/files/immuter.jar) and put that somewhere in your
project. Add the jar to the classpath passed to javac and it will automatically be activated.
Here's an excerpt from an Ant build script:

```java
  <target name="compile" ...>
    <javac ...>
      <classpath>
        <path refid="myproject.classpath"/>
        <fileset dir="lib" includes="immuter.jar"/>
      </classpath>
    </javac>
  </target>
```

Note: the immuter plugin (and the imferrer plugin below) *require JDK 1.6*. The compiler plugin
interface changed dramatically between 1.5 and 1.6, and we use the 1.6 API.

## Automatically inferring @var annotations

If you have a body of code that you want to convert to immutable by default, you're in luck! I have
also written an immutability inferencer which you can run on your code and which will (with some
limitations) infer which variables can be immutable and which must remain mutable, and it will
automatically add @var annotations to the latter.

You can run it on your code as follows. Download
[imferrer.jar](http://immutablej.googlecode.com/files/imferrer.jar) and wire it (temporarily) into
your build script as follows:

```java
  <target name="imfer" ...>
    <javac ...>
      <classpath>
        <path refid="myproject.classpath"/>
        <fileset dir="lib" includes="imferrer.jar"/>
      </classpath>
      <compilerarg value="-proc:only"/>
    </javac>
  </target>
```

The imferrer is (currently) not idempotent, so you can't run it on code that already has @var
annotations. Thus you need to *be sure that you do the equivalent of* `ant clean` *on your project
before running the imferrer* to ensure that everything gets properly processed.

Also note, the imferrer *overwrites source files in place* so be sure you're working on a pristine
version control checkout so that you can revert the changes if something goes horribly awry. You
shouldn't encounter any problems, but better safe than sorry.

Once you have run the imferrer, you should be able to compile your code with the immuter plugin and
it will compile right out of the box. You may bump into one of the few corner cases of the
inference algorithm in which case you'll see a compile error that you can easily go in and fix by
adding a @var annotation to the variable that the imferrer erroneously thought was immutable, more
on that below.

*Note*: you only ever run the imferrer *once* on a codebase. Once you've inferrered immutability
for existing code, then it's up to you to correctly mark things mutable or not when you're writing
new code. The whole point of immutable by default is that your code fails to compile when you
accidentally try to mutate a variable that should not be mutable (or more generally, that you
realize when you are causing a variable to need mutability). If we inferred mutability all the
time, then we'd magically make variables mutable without you knowing, which would undermine the
whole purpose.

## Inference limitations

  * The inference is local, so it can only infer immutability for formal method parameters, local variable declarations and private members. Public and protected members may be mutated by code outside the classfile which the imferencer cannot see, so it automatically marks all such members mutable (unless of course they have been manually marked final).
  * The inference does not do flow analysis in a class constructor. It assumes that if a potentially immutable value is *only* assigned in the constructor, then you're probably initializing it for the first time and it can still be considered immutable. Thus it will *not* mark such members as immutable. If there is a path through your constructor that initializes a member twice, then after you run the immuter your code won't compile because that member will have been marked final and you'll need to go manually add a @var annotation. This could be fixed, but I'm lazy.
  * All declared members of anonymous inner classes are considered local from the standpoint of immutability checking because there is no way to access them through any reference to the anonymous inner class. This is not strictly true, there is a way to reference declared members of an anonymous inner class. Did you know that you can do: `new Object() { public int foo; }.foo = 5;`? If you do that, the imferencer will not "see" the assignment to foo and will assume it can be made immutable. Fortunately you are a nice person and you would never do anything so crazy, so you won't run into this problem.
  * The imferencer has to add `import org.immutablej.var` to your class if it adds @var annotations. This import is placed at the top of all your other imports for lack of an obvious better choice. If you have no imports it puts a blank line after your package statement and puts the import there. If you have a space between your package name and the semicolon that follows it, this process will be confused (because we're working with the AST rather than the source text, we can't _see_ that semicolon).

## Inference unlimitations

  * The imferencer looks for any assignments to your variables and if it doesn't see one, it allows
    that variable to remain immutable (yay for immutable by default). This means that variables that
    *used to be* mutable in your code will be made immutable where it does not break your program.
    Win!

## Future enhancements

  * For situations where you know you can recompile the entire codebase, you could instruct the imferencer to assume that protected and public members were amenable to local analysis. This will almost certainly break your build and cause you to have to go in and manually adjust the mutability of some of your public and/or protected members. However, this might be worth the trouble because then you would be sure that everything that could be marked immutable was.
  * Presently the imferrer doesn't remove final declarations that have been made redundant. This shouldn't be hard. It would also be easy to make the immuter emit a warning when it sees a redundant final qualifier. That way you could keep them from creeping back into your codebase.
  * A configuration option could be provided to control where the imferencer puts the @var import. Maybe someone who is as anal about import ordering as I am will do this and send me a patch.
  * Some people probably chafe at seeing `@var` rather than `@Var`. We could easily support both and provide an option to the imferencer to emit one or the other.

## Suppressing the unhandled annotation warning 

Because of the way the javac Annotation Processing API works, it is not possible to tell javac that
your plugin needs to see every source file (which you do by saying you handle all annotation types)
and then tell it that you handled _some_ of those annotations. As a result, you will see a warning
when you compile:

```
warning: No processor claimed any of these annotations: [org.immutablej.var]
```

In fact, if you use any other annotations (like JUnit's @Test annotation for example), javac will
start complaining that nothing is handling those annotations either.

If you *know* that you're not using any other annotation processors, you can pass an argument to
the immuting processor and tell it to go ahead and claim that it handled *all* annotations in all
of your files. This will suppress all such warnings, which is very nice. It will also prevent any
other annotation processor from getting a chance to operate on your code, so don't do it if you
need to use any other annotation processors in conjunction with the immuter.

Here's what that argument looks like in the Ant javac task:

```xml
    <javac ...>
      <compilerarg value="-Aorg.immutablej.handle_star=true"/>
    </javac>
```

## IDE Integration

*Eclipse*: Eclipse uses its own Java compiler totally different from javac and not (AFAIK)
supporting javac's annotation processor interface. Thus using this will probably confuse Eclipse
until a similar plugin can be written for Eclipse. Since I wrote this as a distraction from my
research (which involves an APT that makes changes to javac's AST), I won't get around to doing an
Eclipse version until I'm doing the same thing for my research project. If someone wants to take a
crack at an Eclipse plugin before that, that would be awesome. It shouldn't be very hard.

*NetBeans*: Since NetBeans uses javac internally, it should be possible to let it know about the
immuter annotation processor and have everything work. I don't use NetBeans so I don't know if or
how to do this. If anyone does get this working, let me know and I'll put instructions here.

*IntelliJ*: I don't know what compiler IntelliJ uses under the hood, so I don't know how one would
go about wiring this annotation processor in. Again, help or feedback from an IntelliJ user is most
welcome.

## Feedback

I'd love to hear what you think about the idea, receive patches, long tirades about how I'm
undermining the integrity of the Java language and balkanizing programmers, anything you like! You
can send me email at my Github username `@gmail.com`.
