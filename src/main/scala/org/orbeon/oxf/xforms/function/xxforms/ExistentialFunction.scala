/**
 * Copyright (C) 2013 Orbeon, Inc.
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
import org.orbeon.saxon.expr.{ExpressionTool, XPathContext}
import org.orbeon.saxon.functions.Evaluate.PreparedExpression
import org.orbeon.saxon.trace.Location
import org.orbeon.saxon.trans.{SaxonErrorCode, XPathException}
import org.orbeon.saxon.value.{ObjectValue, BooleanValue}
import scala.util.control.Breaks._

class XXFormsForall extends XFormsFunction with ExistentialFunction  {
    def defaultValue = true
    def returnNonDefaultValue(b: Boolean) = ! b
}

class XXFormsExists extends XFormsFunction with ExistentialFunction  {
    def defaultValue = false
    def returnNonDefaultValue(b: Boolean) = b
}

trait ExistentialFunction extends XFormsFunction with FunctionSupport {

    def defaultValue: Boolean
    def returnNonDefaultValue(b: Boolean): Boolean

    override def evaluateItem(context: XPathContext): BooleanValue = {

        def throwDynamicError() = {
            val err = new XPathException("Second argument to xxf:forall must be an expression prepared using saxon:expression", this)
            err.setXPathContext(context)
            err.setErrorCode(SaxonErrorCode.SXXF0001)
            throw err
        }

        val items = arguments(0).iterate(context)
        val pexpr  =
            Option(arguments(1).evaluateItem(context)) collect
            { case o: ObjectValue        ⇒ o.getObject  } collect
            { case e: PreparedExpression ⇒ e } getOrElse
            { throwDynamicError() }

        val c = context.newCleanContext
        c.setOriginatingConstructType(Location.SAXON_HIGHER_ORDER_EXTENSION_FUNCTION)
        c.setCurrentIterator(items)
        c.openStackFrame(pexpr.stackFrameMap)

        for (i ← 2 to arguments.length - 1) {
            val slot = pexpr.variables(i - 2).getLocalSlotNumber
            c.setLocalVariable(slot, ExpressionTool.eagerEvaluate(arguments(i), c))
        }

        breakable {
            while (true) {
                val next = items.next()
                if (next eq null)
                    break()

                pexpr.expression.evaluateItem(c) match {
                    case b: BooleanValue ⇒
                        if (returnNonDefaultValue(b.getBooleanValue))
                            return ! defaultValue
                    case _ ⇒
                        val e = new XPathException("expression in xxf:forall() must return numeric values")
                        e.setXPathContext(context)
                        throw e
                }
            }
        }

        defaultValue
    }
}