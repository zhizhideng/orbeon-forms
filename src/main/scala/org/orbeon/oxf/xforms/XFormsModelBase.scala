/**
 *  Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.xforms

import org.orbeon.oxf.xforms.event.Dispatch.EventListener
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xforms.xbl.XBLContainer

import java.{util ⇒ ju}
import org.orbeon.oxf.xforms.model.{BindNode, DataModel}
import org.orbeon.oxf.util.Logging
import org.orbeon.oxf.util.ScalaUtils._
import scala.collection.JavaConverters._
import org.orbeon.saxon.om.{StructuredQName, NodeInfo}
import org.orbeon.oxf.xforms.event.{XFormsEvent, Dispatch}
import org.orbeon.oxf.xforms.event.events.{XXFormsInvalidEvent, XXFormsValidEvent}
import collection.mutable
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.common.ValidationException

abstract class XFormsModelBase(val container: XBLContainer, val effectiveId: String, val staticModel: Model) extends Logging {

    // Listeners
    def addListener(eventName: String , listener: EventListener): Unit =
        throw new UnsupportedOperationException

    def removeListener(eventName: String , listener: EventListener): Unit =
        throw new UnsupportedOperationException

    def getListeners(eventName: String) = Seq.empty[EventListener]

    val containingDocument = container.getContainingDocument
    implicit val indentedLogger = containingDocument.getIndentedLogger(XFormsModel.LOGGING_CATEGORY)

    val deferredActionContext = new DeferredActionContext(container)

    // TEMP: implemented in Java subclass until we move everything to Scala
    def resetAndEvaluateVariables(): Unit
    def getBinds: XFormsModelBinds
    def getInstances: ju.List[XFormsInstance]
    def mustBindValidate: Boolean

    private lazy val _schemaValidator =
        new XFormsModelSchemaValidator(staticModel.element, indentedLogger) |!> (_.loadSchemas(containingDocument))

    def schemaValidator = _schemaValidator
    def hasSchema = _schemaValidator.hasSchema

    def getSchemaURIs: Array[String] =
        if (hasSchema)
            _schemaValidator.getSchemaURIs
        else
            null

    def doRebuild(): Unit = {
        if (deferredActionContext.rebuild) {
            try {
                resetAndEvaluateVariables()
                if (hasInstancesAndBinds) {
                    // NOTE: contextStack.resetBindingContext(this) called in evaluateVariables()
                    getBinds.rebuild()

                    // Controls may have @bind or bind() references, so we need to mark them as dirty. Will need dependencies for controls to fix this.
                    // TODO: Handle XPathDependencies
                    container.requireRefresh()
                }
            } finally
                deferredActionContext.rebuild = false
        }
        containingDocument.getXPathDependencies.rebuildDone(staticModel)
    }

    // Recalculate and revalidate are a combined operation
    // See https://github.com/orbeon/orbeon-forms/issues/1650
    def doRecalculateRevalidate(applyDefaults: Boolean): Unit = {

        val instances = getInstances.asScala

        // Do the work if needed
        // TODO: Ensure that there are no side effects via event dispatch.
        def recalculateRevalidate: Option[collection.Set[String]] =
            if (deferredActionContext.recalculateRevalidate) {
                try {
                    doRecalculate(applyDefaults)
                    containingDocument.getXPathDependencies.recalculateDone(staticModel)

                    // Validate only if needed, including checking the flags, because if validation state is clean, validation
                    // being idempotent, revalidating is not needed.
                    val mustRevalidate = instances.nonEmpty && (mustBindValidate || hasSchema)

                    if (mustRevalidate) {
                        val invalidInstances = doRevalidate()
                        containingDocument.getXPathDependencies.revalidateDone(staticModel)

                        Some(invalidInstances)
                    } else
                        None
                } finally
                    deferredActionContext.recalculateRevalidate = false
            } else
                None

        // Gather events to dispatch, at most one per instance, and only if validity has changed
        // NOTE: It is possible, with binds and the use of xxf:instance(), that some instances in
        // invalidInstances do not belong to this model. Those instances won't get events with the dispatching
        // algorithm below.
        def createAndCommitValidationEvents(invalidInstancesIds: collection.Set[String]): Seq[XFormsEvent] = {

            val changedInstances =
                for {
                    instance           ← instances
                    previouslyValid    = instance.valid
                    currentlyValid     = ! invalidInstancesIds(instance.getEffectiveId)
                    if previouslyValid != currentlyValid
                } yield
                    instance

            // Update instance validity
            for (instance ← changedInstances)
                instance.valid = ! instance.valid

            // Create events
            for (instance ← changedInstances)
            yield
                if (instance.valid) new XXFormsValidEvent(instance) else new XXFormsInvalidEvent(instance)
        }

        val validationEvents =
            recalculateRevalidate map createAndCommitValidationEvents getOrElse Nil

        // Dispatch all events
        for (event ← validationEvents)
            Dispatch.dispatchEvent(event)
    }

    private def doRecalculate(applyDefaults: Boolean): Unit =
        withDebug("performing recalculate", List("model" → effectiveId)) {

            val hasVariables = ! staticModel.variablesSeq.isEmpty

            // Re-evaluate top-level variables if needed
            if (hasInstancesAndBinds || hasVariables)
                resetAndEvaluateVariables()

            // Apply calculate binds
            if (hasInstancesAndBinds)
                getBinds.applyCalculateBinds(applyDefaults)
        }

    private def doRevalidate(): collection.Set[String] =
        withDebug("performing revalidate", List("model" → effectiveId)) {

            val instances = getInstances.asScala
            val invalidInstancesIds = mutable.LinkedHashSet[String]()

            // Clear schema validation state
            // NOTE: This could possibly be moved to rebuild(), but we must be careful about the presence of a schema
            for {
                instance ← instances
                instanceMightBeSchemaValidated = hasSchema && instance.isSchemaValidation
                if instanceMightBeSchemaValidated
            } locally {
                DataModel.visitElementJava(instance.rootElement, new DataModel.NodeVisitor {
                    def visit(nodeInfo: NodeInfo) =
                        InstanceData.clearSchemaState(nodeInfo)
                })
            }

            // Validate using schemas if needed
            if (hasSchema)
                for {
                    instance ← instances
                    if instance.isSchemaValidation                   // we don't support validating read-only instances
                    if ! _schemaValidator.validateInstance(instance) // apply schema
                } locally {
                    // Remember that instance is invalid
                    invalidInstancesIds += instance.getEffectiveId
                }

            // Validate using binds if needed
            if (mustBindValidate)
                getBinds.applyValidationBinds(invalidInstancesIds.asJava)

            invalidInstancesIds
        }

    private def hasInstancesAndBinds: Boolean =
        ! getInstances.isEmpty && (getBinds ne null)

    def needRebuildRecalculateRevalidate =
        deferredActionContext.rebuild || deferredActionContext.recalculateRevalidate

    // This is called in response to dispatching xforms-refresh to this model, whether using the xf:refresh
    // action or by dispatching the event by hand.

    // NOTE: If the refresh flag is not set, we do not call synchronizeAndRefresh() because that would only have the
    // side effect of performing RRR on models, but  but not update the UI, which wouldn't make sense for xforms-refresh.
    // This said, is unlikely (impossible?) that the RRR flags would be set but not the refresh flag.
    // FIXME: See https://github.com/orbeon/orbeon-forms/issues/1650
    protected def doRefresh(): Unit =
        if (containingDocument.getControls.isRequireRefresh)
            container.synchronizeAndRefresh()

    def getDefaultEvaluationContext: BindingContext

    val variableResolver =
        (variableQName: StructuredQName, xpathContext: XPathContext) ⇒
            staticModel.bindsByName.get(variableQName.getLocalName) match {
                case Some(targetStaticBind) ⇒
                    // Variable value is a bind nodeset to resolve
                    BindVariableResolver.resolveClosestBind(
                        modelBinds          = getBinds,
                        contextBindNodeOpt  = XFormsFunction.context.data.asInstanceOf[Option[BindNode]],
                        targetStaticBind    = targetStaticBind
                    ) getOrElse
                        (throw new IllegalStateException)
                case None ⇒
                    // Try top-level model variables
                    val modelVariables = getDefaultEvaluationContext.getInScopeVariables
                    // NOTE: With XPath analysis on, variable scope has been checked statically
                    Option(modelVariables.get(variableQName.getLocalName)) getOrElse
                        (throw new ValidationException("Undeclared variable in XPath expression: $" + variableQName.getClarkName, staticModel.locationData))
            }
}

class DeferredActionContext(container: XBLContainer) {

    var rebuild = false
    var recalculateRevalidate = false

    def markRebuild()               = rebuild = true
    def markRecalculateRevalidate() = recalculateRevalidate = true

    def markStructuralChange(): Unit = {
        // "XForms Actions that change the tree structure of instance data result in setting all four deferred update
        // flags to true for the model over which they operate"

        rebuild = true
        recalculateRevalidate = true
        container.requireRefresh()
    }

    def markValueChange(isCalculate: Boolean): Unit = {
        // "XForms Actions that change only the value of an instance node results in setting the flags for
        // recalculate, revalidate, and refresh to true and making no change to the flag for rebuild".

        // Only set recalculate when we are not currently performing a recalculate (avoid infinite loop)
        if (! isCalculate)
            recalculateRevalidate = true

        container.requireRefresh()
    }
}