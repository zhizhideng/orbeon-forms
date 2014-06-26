/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.function.{FunctionSupport, XFormsFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.saxon.value.StringValue

class XXFormsLHHA extends XFormsFunction with FunctionSupport {

    override def evaluateItem(xpathContext: XPathContext) = {

        implicit val ctx = xpathContext

        def evaluateControlItem(f: XFormsControl ⇒ String) =
            relevantControl(0) map (control ⇒ StringValue.makeStringValue(f(control))) orNull

        evaluateControlItem(operation match {
            case 0 ⇒ _.getLabel
            case 1 ⇒ _.getHelp
            case 2 ⇒ _.getHint
            case 3 ⇒ _.getAlert
            case _ ⇒ throw new UnsupportedOperationException
        })
    }
}
