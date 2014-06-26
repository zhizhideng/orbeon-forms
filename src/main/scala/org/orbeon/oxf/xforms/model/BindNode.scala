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
package org.orbeon.oxf.xforms.model

import collection.JavaConverters._
import collection.breakOut
import java.util.{List ⇒ JList}
import org.orbeon.oxf.xforms.{XFormsModelBinds, InstanceData}
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels._
import org.orbeon.oxf.xforms.analysis.model.{StaticBind, Model}
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML
import org.w3c.dom.Node.ELEMENT_NODE
import org.dom4j.Node
import org.orbeon.oxf.util.ScalaUtils._

// Holds MIPs associated with a given RuntimeBind iteration
// The constructor automatically adds the BindNode to the instance data node if any.
class BindNode(val parentBind: RuntimeBind, val position: Int, val item: Item) {

    import BindNode._

    require(parentBind ne null)

    val (node, hasChildrenElements) =
        item match {
            case node: NodeInfo ⇒
                val hasChildrenElements = node.getNodeKind == ELEMENT_NODE && XML.hasChildElement(node)
                InstanceData.addBindNode(node, this)
                staticBind.dataType foreach (InstanceData.setBindType(node, _))
                (node, hasChildrenElements)
            case _ ⇒
                (null, false)
        }

    // Current MIP state
    private var _relevant = Model.DEFAULT_RELEVANT // move to public var once all callers are Scala
    private var _readonly = Model.DEFAULT_READONLY // move to public var once all callers are Scala
    private var _required = Model.DEFAULT_REQUIRED // move to public var once all callers are Scala

    private var _invalidTypeValidation: StaticBind#MIP = null
    private var _requiredValidation: StaticBind#MIP    = null

    private var _customMips = Map.empty[String, String]

    // Since there are only 3 levels we should always get an optimized immutable Map
    // For a given level, an empty List is not allowed.
    var failedConstraints = EmptyValidations

    // Failed validations for the given level, including type/required
    def failedValidations(level: ValidationLevel) = level match {
        case level @ ErrorLevel if ! typeValid || ! requiredValid ⇒
            // Add type/required if needed
            (! typeValid     list invalidTypeValidation)     :::
            (! requiredValid list invalidRequiredValidation) :::
            failedConstraints.get(level).getOrElse(Nil)
        case level ⇒
            // Cannot be type/required as those only have ErrorLevel
            failedConstraints(level)
    }

    // Highest failed validation level, including type/required
    def highestValidationLevel = {
        def typeOrRequiredLevel = (! typeValid || ! requiredValid) option ErrorLevel
        def constraintLevel     = LevelsByPriority find failedConstraints.contains

        typeOrRequiredLevel orElse constraintLevel
    }

    // All failed validations, including type/required
    def failedValidationsForAllLevels: Validations =
        if (typeValid && requiredValid)
            failedConstraints
        else
            failedConstraints + (ErrorLevel → failedValidations(ErrorLevel))

    def staticBind = parentBind.staticBind
    def locationData = staticBind.locationData

    def setRelevant(value: Boolean) = this._relevant = value
    def setReadonly(value: Boolean) = this._readonly = value
    def setRequired(value: Boolean) = this._required = value

    def setTypeValid(value: Boolean, mip: StaticBind#MIP)     = this._invalidTypeValidation = if (! value) mip else null
    def setRequiredValid(value: Boolean, mip: StaticBind#MIP) = this._requiredValidation    = if (! value) mip else null

    def setCustom(name: String, value: String) = _customMips += name → value

    def relevant        = _relevant
    def readonly        = _readonly
    def required        = _required

    def invalidTypeValidation     = _invalidTypeValidation
    def typeValid                 = _invalidTypeValidation eq null
    def invalidRequiredValidation = _requiredValidation
    def requiredValid             = _requiredValidation eq null

    def constraintsSatisfiedForLevel(level: ValidationLevel) = ! failedConstraints.contains(level)
    def valid = typeValid && requiredValid && constraintsSatisfiedForLevel(ErrorLevel)

    def ancestorOrSelfBindNodes =
        Iterator.iterate(this)(_.parentBind.parentIteration) takeWhile (_ ne null)
}

object BindNode {

    type Validations = Map[ValidationLevel, List[StaticBind#MIP]]

    val EmptyValidations: Validations = Map()

    // NOTE: This takes the first custom MIP of a given name associated with the bind. We do store multiple
    // ones statically, but don't have yet a solution to combine them. Should we string-join them? See also
    // XFormsModelBindsBase.evaluateCustomMIP.
    def collectAllCustomMIPs(bindNodes: JList[BindNode]) =
        if (bindNodes eq null)
            Map.empty[String, String]
        else if (bindNodes.size == 1)
            bindNodes.get(0)._customMips
        else
            bindNodes.asScala.reverse.foldLeft(Map.empty[String, String])(_ ++ _._customMips)

    // Get all failed constraints for all levels, combining BindNodes if needed
    def failedValidationsForAllLevels(node: Node): Validations =
        collectFailedValidationsForAllLevels(Option(InstanceData.getLocalInstanceData(node)) map (_.getBindNodes.asScala) getOrElse Nil)

    private def collectFailedValidationsForAllLevels(bindNodes: Seq[BindNode]): Validations =
        if (bindNodes.isEmpty)
            EmptyValidations
        else if (bindNodes.size == 1)
            bindNodes(0).failedValidationsForAllLevels
        else {
            // This is rather inefficient but hopefully rare
            val buildersByLevel = collection.mutable.Map[ValidationLevel, collection.mutable.Builder[StaticBind#MIP, List[StaticBind#MIP]]]()

            for {
                level       ← LevelsByPriority
                bindNode    ← bindNodes
                failed      = bindNode.failedValidationsForAllLevels.getOrElse(level, Nil)
                if failed.nonEmpty
            } locally {
                val builder = buildersByLevel.getOrElseUpdate(level, List.newBuilder[StaticBind#MIP])
                builder ++= failed
            }

            buildersByLevel.map { case (k, v) ⇒ k → v.result()} (breakOut)
        }

    // Get all failed constraints for the highest level only, combining BindNodes if needed
    def failedValidationsForHighestLevel(nodeInfo: NodeInfo)  =
        collectFailedValidationsForHighestLevel(Option(InstanceData.getLocalInstanceData(nodeInfo, false)) map (_.getBindNodes.asScala) getOrElse Nil)

    private def collectFailedValidationsForHighestLevel(bindNodes: Seq[BindNode]): Option[(ValidationLevel, List[StaticBind#MIP])] =
        collectFailedValidationsForLevel(bindNodes, _.highestValidationLevel)
    
    private def collectFailedValidationsForLevel(bindNodes: Seq[BindNode], findLevel: BindNode ⇒ Option[ValidationLevel]): Option[(ValidationLevel, List[StaticBind#MIP])] =
        if (bindNodes.isEmpty)
            None
        else {
            val consideredLevels = bindNodes flatMap (node ⇒ findLevel(node) map (level ⇒ (level, node)))
            val highestLevelOpt  = consideredLevels.nonEmpty option (consideredLevels map (_._1) max)

            highestLevelOpt map {
                highestLevel ⇒

                    val failedForHighest =
                        consideredLevels.toList collect {
                            case (`highestLevel`, node) ⇒ node.failedValidations(highestLevel)
                        } flatten

                    (highestLevel, failedForHighest)
            }
        }
}

// Bind node that also contains nested binds
class BindIteration(parentBind: RuntimeBind, position: Int, item: Item, childrenBindsHaveSingleNodeContext: Boolean, childrenStaticBinds: Seq[StaticBind])
    extends BindNode(parentBind, position, item) {

    require(childrenStaticBinds.size > 0)

    def forStaticId = parentBind.staticId

    // Iterate over children and create children binds
    val childrenBinds =
        for (staticBind ← childrenStaticBinds)
            yield new RuntimeBind(parentBind.model, staticBind, this, childrenBindsHaveSingleNodeContext)

    def applyBinds(bindRunner: XFormsModelBinds.BindRunner): Unit =
        for (currentBind ← childrenBinds)
            currentBind.applyBinds(bindRunner)

    def findChildBindByStaticId(bindId: String) =
        childrenBinds find (_.staticBind.staticId == bindId)
}