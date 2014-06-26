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
package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.FormRunner
import FormRunner.{recombineQuery ⇒ _, _}
import collection.mutable.ListBuffer
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{XPath, Logging}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.scaxon.XML._
import util.Try
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import ProcessParser.{RecoverCombinator, ThenCombinator}

// Implementation of simple processes
//
// - A process is usually associated with a Form Runner button.
// - A process can have a name which translates into a definition defined in a property.
// - The property specifies a sequence of actions separated by combinators.
// - Actions are predefined, but some of them are configurable.
//
object SimpleProcess extends ProcessInterpreter with FormRunnerActions with XFormsActions with Logging {

    implicit val logger = containingDocument.getIndentedLogger("process")

    override def extensionActions = AllowedFormRunnerActions ++ AllowedXFormsActions

    // All XPath runs in the context of the main form instance's root element
    def xpathContext = topLevelInstance(FormModel, "fr-form-instance") map (_.rootElement) orNull
    def xpathFunctionLibrary = XFormsFunctionLibrary
    def xpathFunctionContext = XPath.functionContext.orNull

    // NOTE: Clear the PDF URL *before* the process, because if we clear it after, it will be already cleared during the
    // second pass of a two-pass submission.
    // TODO: Delete temp file if any.
    override def beforeProcess() = Try(setvalue(pdfURLInstanceRootElement, ""))

    override def processError(t: Throwable) =
        tryErrorMessage(Map(Some("resource") → "process-error"))

    def writeSuspendedProcess(process: String) =
        setvalue(topLevelInstance(PersistenceModel, "fr-processes-instance").get.rootElement, process)

    def readSuspendedProcess =
        topLevelInstance(PersistenceModel, "fr-processes-instance").get.rootElement.stringValue

    // Search first in properties, then try legacy workflow-send
    def findProcessByName(scope: String, name: String) = {
        implicit val formRunnerParams = FormRunnerParams()

        // The scope is interpreted as a property prefix
        formRunnerProperty(scope + '.' + name) flatMap
        nonEmptyOrNone orElse // don't accept an existing but blank property [why?]
        buildProcessFromLegacyProperties(name)
    }

    // Legacy: build "workflow-send" process based on properties
    private def buildProcessFromLegacyProperties(buttonName: String)(implicit p: FormRunnerParams) = {

        def booleanPropertySet(name: String) = booleanFormRunnerProperty(name)
        def stringPropertySet (name: String) = formRunnerProperty(name) flatMap nonEmptyOrNone isDefined

        buttonName match {
            case "workflow-send" ⇒
                val isLegacySendEmail       = booleanPropertySet("oxf.fr.detail.send.email")
                val isLegacyNavigateSuccess = stringPropertySet("oxf.fr.detail.send.success.uri")
                val isLegacyNavigateError   = stringPropertySet("oxf.fr.detail.send.error.uri")

                val buffer = ListBuffer[String]()

                buffer += "require-uploads"
                buffer += ThenCombinator.name
                buffer += "require-valid"
                buffer += ThenCombinator.name
                buffer += "save"
                buffer += ThenCombinator.name
                buffer += """success-message("save-success")"""

                if (isLegacySendEmail) {
                    buffer += ThenCombinator.name
                    buffer += "email"
                }

                // TODO: Pass `content = "pdf-url"` if isLegacyCreatePDF. Requires better parsing of process arguments.
                //def isLegacyCreatePDF = isLegacyNavigateSuccess && booleanPropertySet("oxf.fr.detail.send.pdf")

                // Workaround is to change config from oxf.fr.detail.send.pdf = true to oxf.fr.detail.send.success.content = "pdf-url"
                if (isLegacyNavigateSuccess) {
                    buffer += ThenCombinator.name
                    buffer += """send("oxf.fr.detail.send.success")"""
                }

                if (isLegacyNavigateError) {
                    buffer += RecoverCombinator.name
                    buffer += """send("oxf.fr.detail.send.error")"""
                }

                Some(buffer mkString " ")
            case _ ⇒
                None
        }
    }
}
