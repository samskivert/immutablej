//
// $Id$

package org.immutablej.imferrer;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Patches a source file by inserting or deleting characters at specified offsets.
 */
public class Patcher
{
    /**
     * Notes an insertion to be applied at the specified character offset. The offset is relative
     * to the unedited file, thus edits need not account for one another's changes.
     */
    public void insert (int pos, final String text)
    {
        _edits.add(new Edit(pos) {
            public String insert () {
                return text;
            }
        });
    }

    /**
     * Notes a removal to be applied at the specified character offset. The offset is relative to
     * the unedited file, thus edits need not account for one another's changes.
     */
    public void remove (int pos, final int chars)
    {
        _edits.add(new Edit(pos) {
            public int skip () {
                return chars;
            }
        });
    }

    /**
     * Clears all registered insertions and removals.
     */
    public void clear ()
    {
        _edits.clear();
    }

    /**
     * Applies all registered edits to the specified input file, writing the results to the
     * supplied output.
     */
    public void apply (File input, Writer output)
        throws IOException
    {
        apply(fileToString(input), output);
    }

    /**
     * Applies all registered edits to the supplied input string, writing the results to the
     * supplied output.
     */
    public void apply (String input, Writer output)
        throws IOException
    {
        // order our edits by increasing position
        Collections.sort(_edits);

        for (int pos = 0, max = input.length(), eidx = 0; pos < max; eidx++) {
            if (eidx >= _edits.size()) {
                output.write(input.substring(pos, max));
                pos = max;
            } else {
                Edit edit = _edits.get(eidx);
                if (pos < edit.pos) {
                    output.write(input.substring(pos, edit.pos));
                }
                output.write(edit.insert());
                pos = edit.pos + edit.skip();
            }
        }
    }

    protected static String fileToString (File file)
        throws IOException
    {
        long length = file.length();
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Ha ha. You're very funny.");
        }

        FileReader reader = null;
        try {
            reader = new FileReader(file);
            char[] buf = new char[(int)length]; // may be more than we need
            int read = reader.read(buf);
            return new String(buf, 0, read);

        } finally {
            try { // boy Java is awesome
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace(System.err);
            }
        }
    }

    protected static class Edit implements Comparable<Edit> {
        public final int pos;

        public Edit (int pos) {
            this.pos = pos;
        }
        public int skip () {
            return 0;
        }
        public String insert () {
            return "";
        }

        // from interface Comparable<Edit>
        public int compareTo (Edit other) {
            return (pos < other.pos) ? -1 : ((pos == other.pos) ? 0 : 1);
        }
    }

    protected List<Edit> _edits = new ArrayList<Edit>();
}
