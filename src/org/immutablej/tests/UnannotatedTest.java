//
// $Id$

package org.immutablej.tests;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests that our processor is called even if the annotation appears nowhere in a source file.
 */
public class UnannotatedTest
{
    @Test public void testUnannotated ()
    {
        int foo = 1;
        new Runnable() {
            public void run () {
                // foo is visible here because it's final
                assertEquals(foo, 1);
            }
        }.run();
    }
}
