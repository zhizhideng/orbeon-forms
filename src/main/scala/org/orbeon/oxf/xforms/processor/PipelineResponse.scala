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
package org.orbeon.oxf.xforms.processor

import java.io.{OutputStreamWriter, PrintWriter}
import org.orbeon.oxf.processor.serializer.CachedSerializer
import org.orbeon.oxf.util.{NetUtils, ContentHandlerOutputStream}
import org.orbeon.oxf.pipeline.api.{ExternalContext}
import org.orbeon.oxf.externalcontext.ResponseAdapter
import org.orbeon.oxf.xml.XMLReceiver

object PipelineResponse {
    /*
     * Create a response that writes to the receiver.
     *
     * As of 2011-08-09 this response is used with replace="all" by:
     *
     * - RequestDispatcherSubmission and LocalPortletSubmission
     * - AllReplacer
     *
     * This can be used upon form initialization, or during the 2nd pass of a 2-pass submission.
     */
    def getResponse(xmlReceiver: XMLReceiver, externalContext: ExternalContext): ExternalContext.Response =
        if (xmlReceiver ne null) {
            new ResponseAdapter {

                private var charset: Option[String] = None

                private val contentHandlerOutputStream = new ContentHandlerOutputStream(xmlReceiver, true)
                private lazy val printWriter = new PrintWriter(new OutputStreamWriter(contentHandlerOutputStream, charset.getOrElse(CachedSerializer.DEFAULT_ENCODING)))
                private val originalResponse = externalContext.getResponse

                override def getOutputStream = contentHandlerOutputStream

                // Return this just because Tomcat 5.5, when doing a servlet forward, may ask for one, just to close it!
                override def getWriter = printWriter

                override def setContentType(contentType: String): Unit =
                    setHeader("Content-Type", contentType)

                override def setContentLength(len: Int): Unit =
                    setHeader("Content-Length", Integer.toString(len))

                override def setStatus(status: Int) {
                    // See: http://wiki.orbeon.com/forms/projects/xforms/better-error-handling-for-replace-all-submission
                    contentHandlerOutputStream.setStatusCode(status.toString);
                }

                override def setHeader(name: String, value: String) {
                    // Handle Content-Type
                    if (name.toLowerCase == "content-type") {
                        charset = Option(NetUtils.getContentTypeCharset(value))
                        contentHandlerOutputStream.setContentType(value)
                    }
                    // Don't allow other headers
                }

                override def getNativeResponse = externalContext.getResponse.getNativeResponse
                override def getNamespacePrefix = originalResponse.getNamespacePrefix

                override def setTitle(title: String): Unit =
                    originalResponse.setTitle(title)

                override def rewriteResourceURL(urlString: String, rewriteMode: Int) =
                    originalResponse.rewriteResourceURL(urlString, rewriteMode)

                override def rewriteRenderURL(urlString: String, portletMode: String, windowState: String) =
                    originalResponse.rewriteRenderURL(urlString, portletMode, windowState)

                override def rewriteActionURL(urlString: String, portletMode: String, windowState: String) =
                    originalResponse.rewriteActionURL(urlString, portletMode, windowState)

                override def rewriteRenderURL(urlString: String) =
                    originalResponse.rewriteRenderURL(urlString)

                override def rewriteActionURL(urlString: String) =
                    originalResponse.rewriteActionURL(urlString)
            }
        } else {
            externalContext.getResponse
        }
}