/*******************************************************************************
 * Copyright (C) 2014, 2015, Danilo Pianini and contributors
 * listed in the project's build.gradle or pom.xml file.
 *
 * This file is part of Protelis, and is distributed under the terms of
 * the GNU General Public License, with a linking exception, as described
 * in the file LICENSE.txt in this project's top directory.
 *******************************************************************************/
package org.protelis.vm.util;

import gnu.trove.list.TByteList;
import org.danilopianini.lang.HashUtils;

import java.io.Serializable;
import java.util.Arrays;

/**
 * A CodePath is a trace from the root to some node in a VM execution tree. Its
 * use is to allow particular execution locations to be serialized and compared
 * between different VMs, thereby enabling code alignment. Importantly, the
 * hashCode can be used to uniquely identify CodePath objects, allowing
 * lightweight transmission and comparison.
 */
public class CodePath implements Serializable {

    private static final long serialVersionUID = 5914261026069038877L;
    private static final int ENCODING_BASE = 36;
    private final byte[] path;
    private transient int hash;
    private transient String string;

    /**
     * @param stack
     *            The numerical markers forming an execution trace to be
     *            represented
     */
    public CodePath(final TByteList stack) {
        path = stack.toArray();
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            hash = HashUtils.hash32(path);
        }
        return hash;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof CodePath) {
            final CodePath pc = (CodePath) o;
            return Arrays.equals(path, pc.path);
        }
        return false;
    }

    @Override
    public String toString() {
        if (string == null) {
            final StringBuilder sb = new StringBuilder();
            for (final long l : path) {
                sb.append(Long.toString(l, ENCODING_BASE));
            }
            string = sb.toString();
        }
        return string;
    }

    /**
     * @return a representation of this path as a long array. The returned array
     *         is a defensive copy, i.e. changes to the returned array will NOT
     *         modify this object
     */
    public long[] asLongArray() {
        final long[] res = new long[path.length];
        for(int i = 0; i < path.length; i++) {
            res[i] = path[i];
        }
        return res;
    }

    public byte[] getPath() {
        return path;
    }
}
