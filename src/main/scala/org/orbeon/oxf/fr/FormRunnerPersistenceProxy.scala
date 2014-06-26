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
package org.orbeon.oxf.fr

import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.pipeline.api.ExternalContext.{Response, Request}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.Headers._
import collection.JavaConversions._
import org.orbeon.oxf.resources.handler.HTTPURLConnection
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.scaxon.XML._
import javax.xml.transform.stream.StreamResult
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.externalcontext.URLRewriter
import collection.JavaConverters._
import org.orbeon.oxf.util.{XPath, URLRewriterUtils, NetUtils}

/**
 * The persistence proxy processor:
 *
 * - proxies GET, PUT, DELETE and POST to the appropriate persistence implementation
 * - sets persistence implementation headers
 * - calls all active persistence implementations to aggregate form metadata
 */
class FormRunnerPersistenceProxy extends ProcessorImpl {

    private val FormPath                   = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/form/([^/]+))""".r
    private val DataPath                   = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/(data|draft)/([^/]+)/([^/]+))""".r
    private val DataCollectionPath         = """/fr/service/persistence(/crud/([^/]+)/([^/]+)/data/)""".r
    private val SearchPath                 = """/fr/service/persistence(/search/([^/]+)/([^/]+))""".r
    private val PublishedFormsMetadataPath = """/fr/service/persistence/form(/([^/]+)(/([^/]+))?)?""".r

    private val ParametersToForward = Set("document", "valid")

    // Start the processor
    override def start(pipelineContext: PipelineContext) {
        val ec = NetUtils.getExternalContext
        proxyRequest(ec.getRequest, ec.getResponse)
    }

    // Proxy the request to the appropriate persistence implementation
    def proxyRequest(request: Request, response: Response) {
        val incomingPath = request.getRequestPath
        incomingPath match {
            case FormPath(path, app, form, _)                   ⇒ proxyRequest(request, response, app, form, "form", path)
            case DataPath(path, app, form, _, _, _)             ⇒ proxyRequest(request, response, app, form, "data", path)
            case DataCollectionPath(path, app, form)            ⇒ proxyRequest(request, response, app, form, "data", path)
            case SearchPath(path, app, form)                    ⇒ proxyRequest(request, response, app, form, "data", path)
            case PublishedFormsMetadataPath(path, app, _, form) ⇒ proxyPublishedFormsMetadata(request, response, Option(app), Option(form), path)
            case _ ⇒ throw new OXFException("Unsupported path: " + incomingPath)
        }
    }

    // Proxy the request depending on app/form name and whether we are accessing form or data
    private def proxyRequest(request: Request, response: Response, app: String, form: String, formOrData: String, path: String): Unit = {

        def buildQueryString =
            NetUtils.encodeQueryString(request.getParameterMap.asScala filter (entry ⇒ ParametersToForward(entry._1)) asJava)

        // Get persistence implementation target URL and configuration headers
        val (persistenceBaseURL, headers) = FormRunner.getPersistenceURLHeaders(app, form, formOrData)
        val connection = proxyEstablishConnection(request, NetUtils.appendQueryString(dropTrailingSlash(persistenceBaseURL) + path, buildQueryString), headers)
        // Proxy status code
        response.setStatus(connection.getResponseCode)
        // Proxy incoming headers
        filterCapitalizeAndCombineHeaders(connection.getHeaderFields, out = false) foreach (response.setHeader _).tupled
        copyStream(connection.getInputStream, response.getOutputStream)
    }

    private def proxyEstablishConnection(request: Request, uri: String, headers: Map[String, String]) = {
        // Create the absolute outgoing URL
        val outgoingURL = {
            val persistenceBaseAbsoluteURL = URLRewriterUtils.rewriteServiceURL(NetUtils.getExternalContext.getRequest, uri, URLRewriter.REWRITE_MODE_ABSOLUTE)
            URLFactory.createURL(persistenceBaseAbsoluteURL)
        }

        def setPersistenceHeaders(connection: HTTPURLConnection) {
            for ((name, value) ← headers)
                connection.setRequestProperty(capitalizeCommonOrSplitHeader(name), value)
        }

        def proxyOutgoingHeaders(connection: HTTPURLConnection) =
            filterCapitalizeAndCombineHeaders(request.getHeaderValuesMap, out = true) foreach (connection.setRequestProperty _).tupled

        if (! Set("GET", "DELETE", "PUT", "POST")(request.getMethod))
            throw new OXFException("Unsupported method: " + request.getMethod)

        // Prepare connection
        val doOutput = Set("PUT", "POST")(request.getMethod)
        val connection = outgoingURL.openConnection.asInstanceOf[HTTPURLConnection]

        connection.setDoInput(true)
        connection.setDoOutput(doOutput)
        connection.setRequestMethod(request.getMethod)

        setPersistenceHeaders(connection)
        proxyOutgoingHeaders(connection)

        // Write body if needed
        // NOTE: HTTPURLConnection requires setting the body before calling connect()
        if (doOutput) {
            // Ask the request generator first, as the body might have been read already
            // Q: Could this be handled automatically in ExternalContext?
            val is = RequestGenerator.getRequestBody(PipelineContext.get) match {
                case bodyURL: String ⇒ NetUtils.uriToInputStream(bodyURL)
                case _ ⇒ request.getInputStream
            }

            copyStream(is, connection.getOutputStream)
        }

        connection.connect()
        connection
    }

    /**
     * Proxies the request to every configured persistence layer to get the list of the forms,
     * and aggregates the results.
     */
    private def proxyPublishedFormsMetadata(request: Request, response: Response, app: Option[String], form: Option[String], path: String): Unit = {
        val propertySet = Properties.instance.getPropertySet

        val providers = {
            (app, form) match {
                case (Some(appName), Some(formName)) ⇒
                    // Get the specific provider for this app/form
                    FormRunner.findProvider(appName, formName, "form").toList
                case _ ⇒
                    // Get providers independently from app/form
                    // NOTE: Could also optimize case where only app is provided, but no callers as of 2013-10-21.
                    def providersUsedInPropertiesForFormDefinition = (
                        propertySet.propertiesStartsWith(FormRunner.PersistenceProviderPropertyPrefix, matchWildcards = false)
                        filterNot (_ endsWith ".data") // exclude `.data` to handle the case of `.*` which covers both `.data` and `.form` endings
                        map       propertySet.getString
                        distinct
                    )

                    // See https://github.com/orbeon/orbeon-forms/issues/1186
                    providersUsedInPropertiesForFormDefinition filter FormRunner.isActiveProvider
            }
        }

        val formElements = providers map FormRunner.getPersistenceURLHeadersFromProvider flatMap { case (baseURI, headers) ⇒
            // Read all the forms for the current service
            val serviceURI = baseURI + "/form" + Option(path).getOrElse("")

            // TODO: Handle connection.getResponseCode.
            useAndClose(proxyEstablishConnection(request, serviceURI, headers).getInputStream) { is ⇒
                val forms = TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, serviceURI, false, false)
                forms \\ "forms" \\ "form"
            }
        }

        // Aggregate and serialize
        // TODO: Add @operations="|admin" based on FB permissions. It is better if this is done in a centralized way.
        // See https://github.com/orbeon/orbeon-forms/issues/1316
        val documentElement = elementInfo("forms")
        XFormsAPI.insert(into = documentElement, origin = formElements)

        response.setContentType("application/xml")
        TransformerUtils.getXMLIdentityTransformer.transform(documentElement, new StreamResult(response.getOutputStream))
    }
}