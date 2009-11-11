//
// $Id$

package org.immutablej.tests;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Test;
import static org.junit.Assert.*;

import org.immutablej.imferrer.Patcher;

/**
 * Tests our file patcher.
 */
public class PatcherTest
{
    @Test public void testInsert ()
        throws IOException
    {
        Patcher patcher = new Patcher();
        patcher.insert(PROGRAM.indexOf("int foo"), "@var ");
        patcher.insert(PROGRAM.indexOf("int bar"), "@var ");
        StringWriter out = new StringWriter();
        patcher.apply(PROGRAM, out);
        assertEquals("public class Test {\n" +
                     "    public @var int foo;\n" +
                     "    public @var int bar;\n" +
                     "    public final int baz;\n" +
                     "    public final int bif;\n" +
                     "}", out.toString());
    }

    @Test public void testRemove ()
        throws IOException
    {
        Patcher patcher = new Patcher();
        patcher.remove(PROGRAM.indexOf("final int baz"), "final ".length());
        patcher.remove(PROGRAM.indexOf("final int bif"), "final ".length());
        StringWriter out = new StringWriter();
        patcher.apply(PROGRAM, out);
        assertEquals("public class Test {\n" +
                     "    public int foo;\n" +
                     "    public int bar;\n" +
                     "    public int baz;\n" +
                     "    public int bif;\n" +
                     "}", out.toString());
    }

    @Test public void testBoth ()
        throws IOException
    {
        Patcher patcher = new Patcher();
        patcher.insert(PROGRAM.indexOf("int foo"), "@var ");
        patcher.insert(PROGRAM.indexOf("int bar"), "@var ");
        patcher.remove(PROGRAM.indexOf("final int baz"), "final ".length());
        patcher.remove(PROGRAM.indexOf("final int bif"), "final ".length());
        StringWriter out = new StringWriter();
        patcher.apply(PROGRAM, out);
        assertEquals("public class Test {\n" +
                     "    public @var int foo;\n" +
                     "    public @var int bar;\n" +
                     "    public int baz;\n" +
                     "    public int bif;\n" +
                     "}", out.toString());
    }

    protected static final String PROGRAM = "public class Test {\n" +
        "    public int foo;\n" +
        "    public int bar;\n" +
        "    public final int baz;\n" +
        "    public final int bif;\n" +
        "}";
}
