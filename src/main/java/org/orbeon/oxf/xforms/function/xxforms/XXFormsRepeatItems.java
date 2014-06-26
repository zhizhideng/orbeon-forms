/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.function.xxforms;

import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

/**
 * The xxf:repeat-items() function returns the current items for an enclosing repeat.
 */
public class XXFormsRepeatItems extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get repeat id
        final Expression contextIdExpression = (argument.length == 0) ? null : argument[0];
        final String repeatId;
        if (contextIdExpression == null) {
            repeatId = bindingContext().enclosingRepeatId();
        } else {
            repeatId = contextIdExpression.evaluateAsString(xpathContext).toString();
        }

        // Get repeat node-set for given id
        return new ListIterator(bindingContext().repeatItems(repeatId));
    }
}
