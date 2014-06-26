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
package org.orbeon.oxf.xforms.analysis

import collection.JavaConverters._
import model.Model
import collection.mutable.{LinkedHashMap, Buffer}
import org.orbeon.oxf.xforms.event.EventHandlerImpl
import org.orbeon.oxf.xforms.xbl.Scope

// Part analysis: models and instances information
trait PartModelAnalysis extends TransientState {

    self: PartAnalysisImpl ⇒

    private[PartModelAnalysis] val modelsByScope = LinkedHashMap[Scope, Buffer[Model]]()
    private[PartModelAnalysis] val modelsByPrefixedId = LinkedHashMap[String, Model]()
    private[PartModelAnalysis] val modelByInstancePrefixedId = LinkedHashMap[String, Model]()

    def getModel(prefixedId: String) =
        modelsByPrefixedId.get(prefixedId).orNull

    def getModelByInstancePrefixedId(prefixedId: String) =
        modelByInstancePrefixedId.get(prefixedId).orNull

    def getInstances(modelPrefixedId: String) =
        modelsByPrefixedId.get(modelPrefixedId).toSeq flatMap (_.instances.values) asJava

    def defaultModel =
        getDefaultModelForScope(startScope)

    def getDefaultModelForScope(scope: Scope) =
        modelsByScope.get(scope) flatMap (_.headOption)

    def getModelByScopeAndBind(scope: Scope, bindStaticId: String) =
        modelsByScope.get(scope) flatMap
            (_ find (_.bindsById.contains(bindStaticId))) orNull

    def getModelsForScope(scope: Scope) =
        modelsByScope.get(scope) getOrElse Seq()

    def findInstancePrefixedId(startScope: Scope, instanceStaticId: String): String = {
        var currentScope = startScope
        while (currentScope ne null) {
            for (model ← getModelsForScope(currentScope)) {
                if (model.instancesMap.containsKey(instanceStaticId)) {
                    return currentScope.prefixedIdForStaticId(instanceStaticId)
                }
            }
            currentScope = currentScope.parent
        }
        null
    }

    protected def indexModel(model: Model, eventHandlers: Buffer[EventHandlerImpl]) {
        val models = modelsByScope.getOrElseUpdate(model.scope, Buffer[Model]())
        models += model
        modelsByPrefixedId += model.prefixedId → model

        for (instance ← model.instances.values)
            modelByInstancePrefixedId += instance.prefixedId → model
    }

    protected def deindexModel(model: Model) {
        modelsByScope.get(model.scope) foreach (_ -= model)
        modelsByPrefixedId -= model.prefixedId

        for (instance ← model.instances.values)
            modelByInstancePrefixedId -= instance.prefixedId
    }

    protected def analyzeModelsXPath() =
        for {
            (scope, models) ← modelsByScope
            model ← models
        } yield
            model.analyzeXPath()

    override def freeTransientState() = {
        super.freeTransientState()

        for (model ← modelsByPrefixedId.values)
            model.freeTransientState()
    }
}