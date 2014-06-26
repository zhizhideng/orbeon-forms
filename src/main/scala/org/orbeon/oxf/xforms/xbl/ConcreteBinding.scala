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
package org.orbeon.oxf.xforms.xbl

import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.dom4j.{Element, Document}
import org.orbeon.oxf.xml.SAXStore

case class ConcreteBinding(
    abstractBinding: AbstractBinding,
    innerScope: Scope,                  // each binding defines a new scope
    outerScope: Scope,                  // this binding's outer scope
    handlers: Seq[Element],             // annotated event handler elements
    models: Seq[Element],               // annotated implementation model elements
    templateTree: SAXStore,             // template with relevant markup for output, including XHTML when needed
    compactShadowTree: Document         // without full content, only the XForms controls
) {
    require(abstractBinding.bindingId.isDefined, "missing id on XBL binding for " + Dom4jUtils.elementToDebugString(abstractBinding.bindingElement))

    def bindingId = abstractBinding.bindingId.get
}
