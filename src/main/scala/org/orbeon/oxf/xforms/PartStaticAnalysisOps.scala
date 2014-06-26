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

import analysis.ElementAnalysis
import analysis.model.Model
import org.dom4j.Element
import org.orbeon.oxf.xml.NamespaceMapping
import xbl.Scope

// Operations on a part that are used during static analysis
trait PartStaticAnalysisOps {

    def getNamespaceMapping(prefix: String, element: Element): NamespaceMapping

    def getModel(prefixedId: String): Model
    def getDefaultModelForScope(scope: Scope): Option[Model]
    def getModelByInstancePrefixedId(prefixedId: String): Model
    def getModelByScopeAndBind(scope: Scope, bindStaticId: String): Model
    def findInstancePrefixedId(startScope: Scope, instanceStaticId: String): String
    
    def getControlAnalysis(prefixedId: String): ElementAnalysis
    def scopeForPrefixedId(prefixedId: String): Scope
    def searchResolutionScopeByPrefixedId(prefixedId: String): Scope
}