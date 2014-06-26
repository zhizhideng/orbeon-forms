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
package org.orbeon.oxf.fr

import org.dom4j.{QName, Document}
import org.orbeon.oxf.pipeline.api.{PipelineContext}
import org.orbeon.oxf.processor.SimpleProcessor
import org.orbeon.oxf.properties.{PropertySet, Properties}
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.{XMLReceiver, TransformerUtils, Dom4j}
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

// Processor to replace or add resources based on properties
//
// An property looks like: oxf.fr.resource.*.*.en.detail.labels.save
//
// NOTE: We used to do this in XSLT, but when it came to implement *adding* missing resources, the level of complexity
// increased too much and readability would have suffered so we rewrote in Scala.
class ResourcesPatcher extends SimpleProcessor  {

    def generateData(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

        // Read inputs
        val resourcesDocument = readInputAsDOM4J(pipelineContext, "data")
        val instanceElement   = new DocumentWrapper(readInputAsDOM4J(pipelineContext, "instance"), null, XPath.GlobalConfiguration) \ *

        val app  = instanceElement \ "app"  stringValue
        val form = instanceElement \ "form" stringValue

        // Transform and write out the document
        ResourcesPatcher.transform(resourcesDocument, app, form)(Properties.instance.getPropertySet)
        TransformerUtils.writeDom4j(resourcesDocument, xmlReceiver)
    }
}

object ResourcesPatcher {

    def transform(resourcesDocument: Document, app: String, form: String)(implicit properties: PropertySet): Unit = {

        val resourcesElement = new DocumentWrapper(resourcesDocument, null, XPath.GlobalConfiguration) \ *

        val propertyNames = properties.propertiesStartsWith("oxf.fr.resource" :: app :: form :: Nil mkString ".")

        // In 4.6 summary/detail buttons are at the top level
        def filterPathForBackwardCompatibility(path: Seq[String]) = path take 2 match {
            case Seq("detail" | "summary", "buttons") ⇒ path drop 1
            case _                                    ⇒ path
        }

        val langPathValue =
            for {
                name   ← propertyNames
                tokens = name split """\."""
                lang   = tokens(5)
                path   = filterPathForBackwardCompatibility(tokens drop 6) mkString "/"
            } yield
                (lang, path, properties.getString(name))

        // Return all languages or the language specified if it exists
        // For now we don't support creating new top-level resource elements for new languages.
        def findConcreteLanguages(langOrWildcard: String) = {
            val allLanguages =
                eval(resourcesElement, "resource/@xml:lang/string()").asInstanceOf[Seq[String]]

            val filtered =
                if (langOrWildcard == "*")
                    allLanguages
                else
                    allLanguages filter (_ == langOrWildcard)

            filtered.distinct // there *shouldn't* be duplicate languages in the source
        }

        def resourceElementsForLang(lang: String) =
            eval(resourcesElement, s"resource[@xml:lang = '$lang']").asInstanceOf[Seq[NodeInfo]] map unwrapElement

        // Update or create elements and set values
        for {
            (langOrWildcard, path, value) ← langPathValue
            lang                          ← findConcreteLanguages(langOrWildcard)
            rootForLang                   ← resourceElementsForLang(lang)
        }
            Dom4j.ensurePath(rootForLang, path split "/" map QName.get).setText(value)
    }
}