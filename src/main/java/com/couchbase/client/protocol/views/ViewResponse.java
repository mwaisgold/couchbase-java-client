/**
 * Copyright (C) 2009-2013 Couchbase, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */

package com.couchbase.client.protocol.views;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Holds the response of a queried view.
 */
public abstract class ViewResponse implements Iterable<ViewRow> {
  protected final Collection<ViewRow> rows;
  protected final Collection<RowError> errors;

  public ViewResponse(final Collection<ViewRow> r,
      final Collection<RowError> e) {
    rows = r;
    errors = e;
  }

  public Collection<RowError> getErrors() {
    return errors;
  }

  @Override
  public Iterator<ViewRow> iterator() {
    return rows.iterator();
  }

  public int size() {
    return rows.size();
  }

  public abstract Map<String, Object> getMap();
}
