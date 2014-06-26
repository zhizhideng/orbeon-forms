/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.processor.converter

import org.orbeon.oxf.pipeline.api.{PipelineContext}
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl
import org.orbeon.oxf.processor.{ProcessorInputOutputInfo, ProcessorImpl}
import org.orbeon.oxf.xml.{XMLReceiver, ForwardingXMLReceiver}
import org.orbeon.oxf.xml.XMLConstants.{XHTML_NAMESPACE_URI ⇒ HtmlURI}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

// Perform the following transformation on the input document:
//
// - remove all elements not in the XHTML namespace
// - remove all attributes in a namespace
// - remove the prefix of all XHTML elements
// - remove all other namespace information on elements
// - for XHTML
//   - add the XHTML namespace as default namespace on the root element
//   - all elements in the document are in the XHTML namespace
// - otherwise
//   - don't output any namespace declaration
//   - all elements in the document are in no namespace
//
class PlainHTMLConverter  extends Converter("")
class PlainXHTMLConverter extends Converter(HtmlURI)

abstract class Converter(targetURI: String) extends ProcessorImpl {

    self ⇒

    addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA))
    addOutputInfo(new ProcessorInputOutputInfo(ProcessorImpl.OUTPUT_DATA))

    override def createOutput(outputName: String) =
        addOutput(outputName, new CacheableTransformerOutputImpl(self, outputName) {
            def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

                readInputAsSAX(pipelineContext, ProcessorImpl.INPUT_DATA, new ForwardingXMLReceiver(xmlReceiver) {

                    var level = 0
                    var inXHTMLNamespace = false

                    override def startElement(uri: String, localname: String, qName: String, attributes: Attributes) = {

                        // http://www.w3.org/TR/xslt-xquery-serialization/#xhtml-output: "The serializer SHOULD output
                        // namespace declarations in a way that is consistent with the requirements of the XHTML DTD if
                        // this is possible". We tried to output the document in the XHTML namespace only if the root
                        // element is "{http://www.w3.org/1999/xhtml}html", however the issue then is that in the case
                        // of a fragment, the resulting document is not in the XHTML namespace and the XHTML serializer
                        // is unable to output elements such as <br />. So we have reverted this change and when the
                        // HTML namespace is specified we now always output the document in the XHTML namespace.
                        if (level == 0 && targetURI == HtmlURI) {
                            inXHTMLNamespace = true
                            super.startPrefixMapping("", HtmlURI)
                        }

                        if (uri == HtmlURI)
                            super.startElement(if (inXHTMLNamespace) targetURI else "", localname, localname, filterAttributes(attributes))

                        level += 1
                    }

                    override def endElement(uri: String, localname: String, qName: String) = {

                        level -= 1

                        if (uri == HtmlURI)
                            super.endElement(if (inXHTMLNamespace) targetURI else "", localname, localname)

                        if (level == 0 && inXHTMLNamespace)
                            super.endPrefixMapping("")
                    }

                    // Swallow all namespace mappings
                    override def startPrefixMapping(prefix: String, uri: String) = ()
                    override def endPrefixMapping(prefix: String) = ()

                    // Only keep attributes in no namespace
                    def filterAttributes(attributes: Attributes) = {
                        val length = attributes.getLength

                        // Whether there is at least one attribute in a namespace
                        def hasNamespace: Boolean = {
                            var i = 0
                            while (i < length) {
                                if (attributes.getURI(i) != "")
                                    return true

                                i += 1
                            }
                            false
                        }

                        if (hasNamespace) {
                            val newAttributes = new AttributesImpl

                            var i = 0
                            while (i < length) {
                                if (attributes.getURI(i) == "")
                                    newAttributes.addAttribute(attributes.getURI(i), attributes.getLocalName(i),
                                        attributes.getQName(i), attributes.getType(i), attributes.getValue(i))

                                i += 1
                            }

                            newAttributes
                        } else
                            attributes
                    }
                })
            }
        })
}
