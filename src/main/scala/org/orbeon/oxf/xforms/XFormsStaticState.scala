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
package org.orbeon.oxf.xforms

import org.orbeon.oxf.util.IndentedLogger
import state.AnnotatedTemplate
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.oxf.util.XPath.CompiledExpression

trait XFormsStaticState {

    def getIndentedLogger: IndentedLogger

    def digest: String
    def encodedState: String
    def allowedExternalEvents: Set[String]
    def template: Option[AnnotatedTemplate]

    def topLevelPart: PartAnalysis

    def isCacheDocument: Boolean
    def isClientStateHandling: Boolean
    def isServerStateHandling: Boolean
    def isHTMLDocument: Boolean

    def isXPathAnalysis: Boolean
    def sanitizeInput: String ⇒ String

    def staticProperty(name: String): Any
    def staticBooleanProperty(name: String): Boolean
    def propertyMaybeAsExpression(name: String): Either[Any, CompiledExpression]
    def clientNonDefaultProperties: Map[String, AnyRef]

    def toXML(helper: XMLReceiverHelper)
    def dumpAnalysis()
}