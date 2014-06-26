/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.library

import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.oxf.xforms.function.Last
import org.orbeon.saxon.functions._
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.value._
import org.orbeon.saxon.`type`.Type
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.saxon.om.NamespaceConstant

/**
 * Function library for XPath expressions in XForms.
 */
object XFormsFunctionLibrary extends {
    // Namespace the functions (we wish we had trait constructors!)
    val XFormsIndependentFunctionsNS  = Seq(NamespaceConstant.FN, XFORMS_NAMESPACE_URI)
    val XFormsEnvFunctionsNS          = Seq(NamespaceConstant.FN, XFORMS_NAMESPACE_URI)
    val XFormsXXFormsEnvFunctionsNS   = Seq(XFORMS_NAMESPACE_URI, XXFORMS_NAMESPACE_URI)
    val XFormsFunnyFunctionsNS        = Seq(NamespaceConstant.FN, XFORMS_NAMESPACE_URI)
    val XXFormsIndependentFunctionsNS = Seq(XXFORMS_NAMESPACE_URI)
    val XXFormsEnvFunctionsNS         = Seq(XXFORMS_NAMESPACE_URI)
    val EXFormsFunctionsNS            = Seq(EXFORMS_NAMESPACE_URI)
    val XSLTFunctionsNS               = Seq(NamespaceConstant.FN)
}
    with OrbeonFunctionLibrary
    with XFormsIndependentFunctions
    with XFormsEnvFunctions
    with XFormsXXFormsEnvFunctions
    with XFormsFunnyFunctions
    with XFormsDeprecatedFunctions
    with XXFormsIndependentFunctions
    with XXFormsEnvFunctions
    with EXFormsFunctions
    with XSLTFunctions {

    // For Java callers
    def instance = this

    // Saxon's last() function doesn't do what we need
    Fun("last", classOf[Last], 0, 0, INTEGER, EXACTLY_ONE)

    // Forward these to our own implementation so we can handle PathMap
    Fun("count", classOf[org.orbeon.oxf.xforms.function.Aggregate], Aggregate.COUNT, 1, INTEGER, EXACTLY_ONE,
        Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE, IntegerValue.ZERO)
    )

    Fun("avg", classOf[org.orbeon.oxf.xforms.function.Aggregate], Aggregate.AVG, 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
        Arg(ANY_ATOMIC, ALLOWS_ZERO_OR_MORE, EmptySequence.getInstance())
    )

    Fun("sum", classOf[org.orbeon.oxf.xforms.function.Aggregate], Aggregate.SUM, 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
        Arg(ANY_ATOMIC, ALLOWS_ZERO_OR_MORE),
        Arg(ANY_ATOMIC, ALLOWS_ZERO_OR_ONE)
    )
}