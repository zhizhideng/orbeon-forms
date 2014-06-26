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
package org.orbeon.oxf.xforms.action.actions

import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.common.OXFException
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}

/**
 * 10.1.9 The setvalue Element
 */
class XFormsSetvalueAction extends XFormsAction {

    override def execute(actionContext: DynamicActionContext) {

        val actionInterpreter = actionContext.interpreter
        val actionElement = actionContext.element

        val indentedLogger = actionInterpreter.indentedLogger
        val containingDocument = actionInterpreter.containingDocument
        val contextStack = actionInterpreter.actionXPathContext
        
        val valueExpression = Option(actionElement.attributeValue(XFormsConstants.VALUE_QNAME))

        // Determine value to set
        val valueToSet =
            valueExpression match {
                case Some(valueExpression) ⇒
                    // Value to set is computed with an XPath expression
                    actionInterpreter.evaluateAsString(actionElement, contextStack.getCurrentBindingContext.nodeset, contextStack.getCurrentBindingContext.position, valueExpression)
                case None ⇒
                    // Value to set is static content
                    actionElement.getStringValue
            }
        
        assert(valueToSet ne null)

        // Set the value on target node if possible
        contextStack.getCurrentBindingContext.getSingleItem match {
            case nodeInfo: NodeInfo ⇒
                // NOTE: XForms 1.1 seems to require dispatching xforms-binding-exception in case the target node cannot be
                // written to. But because of the way we now handle errors in actions, we throw an exception instead and
                // action processing is interrupted.
                DataModel.setValueIfChanged(
                    nodeInfo,
                    valueToSet,
                    DataModel.logAndNotifyValueChange(containingDocument, "setvalue", nodeInfo, _, valueToSet, isCalculate = false)(indentedLogger),
                    reason ⇒ throw new OXFException(reason.message)
                )
            case _ ⇒
                // Node doesn't exist: NOP
                if (indentedLogger.isDebugEnabled)
                    indentedLogger.logDebug("xf:setvalue", "not setting instance value", "reason", "destination node not found", "value", valueToSet)
        }
    }
}
