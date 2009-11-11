//
// $Id$

package org.immutablej.tests;

import org.immutablej.var;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests mutability annotation.
 */
public class MutableTest
{
    @Test public void testMutable ()
    {
        int foo = 1;
        // foo = 2; // illegal!

        @var int bar = 1;
        bar = 2;

        testArgs(1, 2);

        new Runnable() {
            public void run () {
                // foo is visible here because it's final
                assertEquals(foo, 1);
                // assertEquals(bar, 2); // illegal!
            }
        }.run();

        // _one = 1; // illegal!
        _two = 2; // legal

        for (@var int ii = 0; ii < 10; ii++) {
            bar = ii;
        }
    }

    protected void testArgs (int one, @var int two)
    {
        // one = 2; // illegal!
        two = 3;
    }

    protected int _one = 0;
    protected @var int _two;
}
