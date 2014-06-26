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
package org.orbeon.oxf.controller

import collection.JavaConverters._
import java.util.regex.Pattern
import java.util.{List ⇒ JList, Map ⇒ JMap}
import org.dom4j.{QName, Document, Element}
import org.orbeon.errorified.Exceptions._
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.pipeline.api.{ExternalContext, PipelineContext}
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult
import org.orbeon.oxf.processor._
import org.orbeon.oxf.processor.pipeline.ast._
import org.orbeon.oxf.processor.pipeline.{PipelineConfig, PipelineProcessor}
import org.orbeon.oxf.resources.ResourceNotFoundException
import org.orbeon.oxf.util.URLRewriterUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.webapp.{ProcessorService, HttpRedirectException, HttpStatusCodeException}
import org.orbeon.oxf.xml.Dom4j
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils._
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.pipeline.api.ExternalContext.Request
import org.orbeon.oxf.util.ScalaUtils._
import scala.util.control.NonFatal

// Orbeon Forms application controller
class PageFlowControllerProcessor extends ProcessorImpl with Logging {

    import PageFlowControllerBuilder._
    import PageFlowControllerProcessor._

    addInputInfo(new ProcessorInputOutputInfo(ControllerInput, ControllerNamespaceURI))

    override def start(pc: PipelineContext) {

        implicit val logger = new IndentedLogger(Logger, "")

        // Get or compile page flow
        val pageFlow = readCacheInputAsObject(pc, getInputByName(ControllerInput), new CacheableInputReader[PageFlow] {
            def read(context: PipelineContext, input: ProcessorInput) = {
                val configRoot = readInputAsDOM4J(pc, ControllerInput).getRootElement
                val controllerValidity = ProcessorImpl.getInputValidity(pc, getInputByName(ControllerInput))
                compile(configRoot, controllerValidity)
            }
        })

        // Run it
        val ec = NetUtils.getExternalContext
        val request = ec.getRequest
        val path = request.getRequestPath
        val method = request.getMethod

        lazy val logParams = Seq("controller" → pageFlow.file.orNull, "method" → method, "path" → path)

        // If required, store information about resources to rewrite in the pipeline context for downstream use, e.g. by
        // oxf:xhtml-rewrite. This allows consumers who would like to rewrite resources into versioned resources to
        // actually know what a "resource" is.
        if (pageFlow.pathMatchers.nonEmpty) {
            Option(pc.getAttribute(PathMatchers).asInstanceOf[JList[PathMatcher]]) match {
                case Some(existingPathMatchers) ⇒
                    // Add if we come after others (in case of nested page flows)
                    val allMatchers = existingPathMatchers.asScala ++ pageFlow.pathMatchers
                    pc.setAttribute(PathMatchers, allMatchers.asJava)
                case None ⇒
                    // Set if we are the first
                    pc.setAttribute(PathMatchers, pageFlow.pathMatchers.asJava)
            }
        }

        def logError(t: Throwable) = {
            error("error caught", logParams)
            error(OrbeonFormatter.format(t))
        }

        def logNotFound(t: Option[Throwable]) = {

            def rootResource = t map getRootThrowable collect {
                case e: ResourceNotFoundException ⇒ e.resource
                case HttpStatusCodeException(_, Some(resource), _) ⇒ resource
            }

            info("not found", logParams ++ (rootResource map ("resource" → _)))
        }

        def logUnauthorized(e: HttpStatusCodeException) =
            info("unauthorized", logParams :+ ("status-code" → e.code.toString))

        // For services: only log and set response code
        def sendError(t: Throwable)                      = { logError(t);        ec.getResponse.setStatus(500) }
        def sendNotFound(t: Option[Throwable])           = { logNotFound(t);     ec.getResponse.setStatus(404) }
        def sendUnauthorized(e: HttpStatusCodeException) = { logUnauthorized(e); ec.getResponse.setStatus(e.code) }

        // For pages: log and try to run routes
        def runErrorRoute(t: Throwable, log: Boolean = true) = {

            if (log) logError(t)

            pageFlow.errorRoute match {
                case Some(errorRoute) ⇒
                    // Run the error route
                    ec.getResponse.setStatus(500)
                    if (ProcessorService.showExceptions)
                        pc.setAttribute(ProcessorService.Throwable, t)
                    errorRoute.process(pc, ec, MatchResult(matches = false), mustAuthorize = false)
                case None ⇒
                    // We don't have an error route so throw instead
                    throw t
            }
        }

        def runNotFoundRoute(t: Option[Throwable]) = {

            logNotFound(t)

            pageFlow.notFoundRoute match {
                case Some(notFoundRoute) ⇒
                    // Run the not found route
                    ec.getResponse.setStatus(404)
                    try notFoundRoute.process(pc, ec, MatchResult(matches = false), mustAuthorize = false)
                    catch { case NonFatal(t) ⇒ runErrorRoute(t) }
                case None ⇒
                    // We don't have a not found route so try the error route instead
                    // Don't log because we already logged above
                    runErrorRoute(t getOrElse new HttpStatusCodeException(404), log = false)
            }
        }

        def runUnauthorizedRoute(e: HttpStatusCodeException) = {

            logUnauthorized(e)

            pageFlow.unauthorizedRoute match {
                case Some(unauthorizedRoute) ⇒
                    // Run the unauthorized route
                    ec.getResponse.setStatus(e.code)
                    unauthorizedRoute.process(pc, ec, MatchResult(matches = false), mustAuthorize = false)
                case None ⇒
                    // We don't have an unauthorized route so throw instead
                    throw e
            }
        }

        // Run the first matching entry if any
        pageFlow.routes.iterator map (route ⇒ route → MatchResult(route.routeElement.pattern, path)) find (_._2.matches) match {
            case Some((route: FileRoute, matchResult)) ⇒
                // Run the given route and let the caller handle errors
                debug("processing file", logParams)
                route.process(pc, ec, matchResult)
            case Some((route: PageOrServiceRoute, matchResult)) ⇒
                debug("processing page/service", logParams)
                // Run the given route and handle "not found" and error conditions
                try route.process(pc, ec, matchResult)
                catch { case NonFatal(t) ⇒
                    getRootThrowable(t) match {
                        case e: HttpRedirectException                            ⇒ ec.getResponse.sendRedirect(e.location, e.serverSide, e.exitPortal)
                        // We don't have a "deleted" route at this point, and thus run the not found route when we found a "resource" to be deleted
                        case e: HttpStatusCodeException if Set(404, 410)(e.code) ⇒ if (route.isPage) runNotFoundRoute(Some(t)) else sendNotFound(Some(t))
                        case e: HttpStatusCodeException if Set(401, 403)(e.code) ⇒ if (route.isPage) runUnauthorizedRoute(e)   else sendUnauthorized(e)
                        case e: ResourceNotFoundException                        ⇒ if (route.isPage) runNotFoundRoute(Some(t)) else sendNotFound(Some(t))
                        case e                                                   ⇒ if (route.isPage) runErrorRoute(t)          else sendError(t)
                    }
                }
            case None ⇒
                // Handle "not found"
                runNotFoundRoute(None)
        }
    }

    // Compile a controller file
    def compile(configRoot: Element, controllerValidity: AnyRef)(implicit logger: IndentedLogger) = {
        // Controller format:
        //
        // - config: files*, page*, epilogue?, not-found-handler?, unauthorized-handler?, error-handler?
        // - files:  @id?, (@path|@path-info), @matcher?, (@mediatype|@mime-type)?, @versioned?
        // - page:   @id?, (@path|@path-info), @matcher?, @default-submission?, @model?, @view?

        val stepProcessorContext = new StepProcessorContext(controllerValidity)
        val locationData = configRoot.getData.asInstanceOf[LocationData]
        val urlBase = Option(locationData) map (_.getSystemID) orNull

        // Gather properties
        implicit val properties = getPropertySet

        def controllerProperty(name: String, default: Option[String] = None, allowEmpty: Boolean = false) =
            att(configRoot, name) orElse Option(properties.getStringOrURIAsString(name, default.orNull, allowEmpty))

        def controllerPropertyQName(name: String, default: Option[QName] = None) =
            Option(extractAttributeValueQName(configRoot, name)) orElse Option(properties.getQName(name, default.orNull))

        val defaultMatcher              = controllerPropertyQName(MatcherProperty, Some(DefaultMatcher)).get
        val defaultInstancePassing      = controllerProperty(InstancePassingProperty, Some(DefaultInstancePassing)).get

        // For these, make sure setting the property to a blank value doesn't cause the default to be used
        // See: https://github.com/orbeon/orbeon-forms/issues/865
        val defaultPagePublicMethods    = stringOptionToSet(controllerProperty(PagePublicMethodsProperty, Some(PagePublicMethods mkString " "), allowEmpty = true))
        val defaultServicePublicMethods = stringOptionToSet(controllerProperty(ServicePublicMethodsProperty, Some(ServicePublicMethods mkString " "), allowEmpty = true))

        // NOTE: We use a global property, not an oxf:page-flow scoped one
        val defaultVersioned =
            att(configRoot, "versioned") map (_.toBoolean) getOrElse isResourcesVersioned

        // NOTE: We support a null epilogue value and the pipeline then uses a plain HTML serializer
        val epilogueElement = configRoot.element("epilogue")
        val epilogueURL     = Option(epilogueElement) flatMap (att(_, "url")) orElse controllerProperty(EpilogueProperty)

        val topLevelElements = Dom4j.elements(configRoot)

         // Prepend a synthetic page for submissions if configured
        val syntheticRoutes: Seq[RouteElement] =
            (controllerProperty(SubmissionPathProperty), controllerProperty(SubmissionModelProperty)) match {
                case (Some(submissionPath), submissionModel @ Some(_)) ⇒
                    Seq(PageOrServiceElement(None, submissionPath, Pattern.compile(submissionPath), None, submissionModel, None, configRoot, SubmissionPublicMethods, isPage = true))
                case _ ⇒
                    Seq()
            }

        val explicitRoutes: Seq[RouteElement] =
            for (e ← topLevelElements filter (e ⇒ Set("files", "page", "service")(e.getName)))
                yield e.getName match {
                    case "files"            ⇒ FileElement(e, defaultMatcher, defaultVersioned)
                    case "page" | "service" ⇒ PageOrServiceElement(e, defaultMatcher, defaultPagePublicMethods, defaultServicePublicMethods)
                }

        val routeElements = syntheticRoutes ++ explicitRoutes

        val pagesElementsWithIds = routeElements collect { case page: PageOrServiceElement if page.id.isDefined ⇒ page }
        val pathIdToPath              = pagesElementsWithIds map (p ⇒ p.id.get → p.path) toMap
        val pageIdToSetvaluesDocument = pagesElementsWithIds map (p ⇒ p.id.get → getSetValuesDocument(p.element)) filter (_._2 ne null) toMap

        val pathMatchers =
            routeElements collect
            { case files: FileElement if files.versioned ⇒ files } map
            (f ⇒ new PathMatcher(f.path, f.mimeType.orNull, f.versioned))

        // Compile the pipeline for the given page element
        def compile(page: PageOrServiceElement) = {
            val ast = createPipelineAST(
                page.element,
                controllerValidity,
                stepProcessorContext,
                urlBase,
                defaultInstancePassing,
                epilogueURL,
                epilogueElement,
                pathIdToPath.asJava,
                pageIdToSetvaluesDocument.asJava)

            // For debugging
            if (logger.isDebugEnabled) {
                val astDocumentHandler = new ASTDocumentHandler
                ast.walk(astDocumentHandler)
                debug("created PFC pipeline", Seq("path" → page.path, "pipeline" → ('\n' + domToPrettyString(astDocumentHandler.getDocument))))
            }

            PipelineProcessor.createConfigFromAST(ast)
        }

        // All routes
        val routes: Seq[Route] =
            for (e ← routeElements)
            yield e match {
                case files: FileElement         ⇒ FileRoute(files)
                case page: PageOrServiceElement ⇒ PageOrServiceRoute(page, compile)
            }

        // Find a handler route
        def handler(elementNames: Set[String]) =
            topLevelElements find (e ⇒ elementNames(e.getName)) flatMap (att(_, "page")) flatMap
                { pageId ⇒ routes collectFirst { case page: PageOrServiceRoute if page.routeElement.id == Some(pageId) ⇒ page } }

        PageFlow(routes, handler(Set("not-found-handler")), handler(Set("unauthorized-handler")), handler(Set("error-handler")), pathMatchers, Option(urlBase))
    }

    def createPipelineAST(
            element: Element,
            controllerValidity: AnyRef,
            stepProcessorContext: StepProcessorContext,
            urlBase: String,
            globalInstancePassing: String,
            epilogueURL: Option[String],
            epilogueElement: Element,
            pageIdToPathInfo: JMap[String, String],
            pageIdToSetvaluesDocument: JMap[String, Document]) =
        new ASTPipeline {

            setValidity(controllerValidity)

            // The pipeline has an input with matcher results
            val matcherParam = addParam(new ASTParam(ASTParam.INPUT, "matches"))

            val epilogueData = new ASTOutput(null, "html")
            val epilogueModelData = new ASTOutput(null, "epilogue-model-data")
            val epilogueInstance = new ASTOutput(null, "epilogue-instance")

            // Page
            handlePage(stepProcessorContext, urlBase, getStatements, element,
                    matcherParam.getName, epilogueData, epilogueModelData,
                    epilogueInstance, pageIdToPathInfo, pageIdToSetvaluesDocument,
                    globalInstancePassing)

            // Epilogue
            addStatement(new ASTChoose(new ASTHrefId(epilogueData)) {
                addWhen(new ASTWhen("not(/*/@xsi:nil = 'true')") {
                    setNamespaces(PageFlowControllerBuilder.NAMESPACES_WITH_XSI_AND_XSLT)
                    handleEpilogue(urlBase, getStatements, epilogueURL.orNull, epilogueElement,
                            epilogueData, epilogueModelData, epilogueInstance)
                })
                addWhen(new ASTWhen() {
                    // Make sure we execute the model if there is a model but no view
                    addStatement(new ASTProcessorCall(NULL_SERIALIZER_PROCESSOR_QNAME) {
                        addInput(new ASTInput("data", new ASTHrefId(epilogueModelData)))
                    })
                })
            })
        }
}

object PageFlowControllerProcessor {

    val Logger = LoggerFactory.createLogger(classOf[PageFlowControllerProcessor])

    val ControllerInput = "controller"
    val ControllerNamespaceURI = "http://www.orbeon.com/oxf/controller"

    // Properties
    val MatcherProperty              = "matcher"
    val InstancePassingProperty      = "instance-passing"
    val SubmissionModelProperty      = "submission-model"
    val SubmissionPathProperty       = "submission-path"
    val EpilogueProperty             = "epilogue"

    val PagePublicMethodsProperty    = "page-public-methods"
    val ServicePublicMethodsProperty = "service-public-methods"
    val AuthorizerProperty           = "authorizer"

    val DefaultMatcher               = new QName("glob")
    val DefaultVisibility            = "private"
    val DefaultInstancePassing       = PageFlowControllerBuilder.INSTANCE_PASSING_REDIRECT

    val PathMatchers                 = "path-matchers"

    val PagePublicMethods            = Set("GET", "HEAD")
    val ServicePublicMethods         = Set.empty[String]
    val SubmissionPublicMethods      = Set("GET", "POST") // Q: do we need GET? PUT?
    val AllPublicMethods             = "#all"

    // Route elements
    sealed trait RouteElement { def id: Option[String]; def path: String; def pattern: Pattern }

    case class FileElement(
            id: Option[String],
            path: String,
            pattern: Pattern,
            mimeType: Option[String],
            versioned: Boolean)
        extends RouteElement

    case class PageOrServiceElement(
            id: Option[String],
            path: String,
            pattern: Pattern,
            defaultSubmission: Option[String],
            model: Option[String],
            view: Option[String],
            element: Element,
            publicMethods: String ⇒ Boolean,
            isPage: Boolean)
        extends RouteElement

    object FileElement {
        // id?, path-info?, matcher?, mime-type?, versioned?
        def apply(e: Element, defaultMatcher: QName, defaultVersioned: Boolean): FileElement = {
            val path = getPath(e)
            FileElement(
                idAtt(e),
                path,
                compilePattern(e, path, defaultMatcher),
                att(e, "mediatype") orElse att(e, "mime-type"), // @mime-type for backward compatibility
                att(e, "versioned") map (_ == "true") getOrElse defaultVersioned)
        }
    }

    object PageOrServiceElement {
        // id?, path-info, matcher?, default-submission?, model?, view?, public-methods?
        def apply(e: Element, defaultMatcher: QName, defaultPagePublicMethods: Set[String], defaultServicePublicMethods: Set[String]): PageOrServiceElement = {

            val isPage = e.getName == "page"

            def localPublicMethods = att(e, "public-methods") map {
                case att if att == AllPublicMethods ⇒ (_: String) ⇒ true
                case att                            ⇒ stringToSet(att)
            }

            def defaultPublicMethods = if (isPage) defaultPagePublicMethods else defaultServicePublicMethods

            val path = getPath(e)
            PageOrServiceElement(
                idAtt(e),
                path,
                compilePattern(e, path, defaultMatcher),
                att(e, "default-submission"),
                att(e, "model"),
                att(e, "view"),
                e,
                localPublicMethods getOrElse defaultPublicMethods,
                isPage)
        }
    }

    // Only read mime types config once (used for serving files)
    lazy val MimeTypes = ResourceServer.readMimeTypeConfig

    // Routes
    sealed trait Route {
        def routeElement: RouteElement
        def process(pc: PipelineContext, ec: ExternalContext, matchResult: MatchResult, authorize: Boolean = true)(implicit logger: IndentedLogger)
    }

    case class FileRoute(routeElement: FileElement) extends Route with Logging {
        // Serve a file by path
        def process(pc: PipelineContext, ec: ExternalContext, matchResult: MatchResult, authorize: Boolean = true)(implicit logger: IndentedLogger) = {
            debug("processing route", Seq("route" → this.toString))
            if (ec.getRequest.getMethod == "GET")
                ResourceServer.serveResource(MimeTypes, ec.getRequest.getRequestPath, routeElement.versioned)
            else
                unauthorized()
        }
    }

    case class PageOrServiceRoute(
            routeElement: PageOrServiceElement,
            compile: PageOrServiceElement ⇒ PipelineConfig)(implicit val propertySet: PropertySet)
        extends Route with Authorization with Logging {

        val isPage    = routeElement.isPage
        val isService = ! isPage

        // Compile pipeline lazily
        lazy val pipelineConfig = compile(routeElement)

        // Run a page
        def process(pc: PipelineContext, ec: ExternalContext, matchResult: MatchResult, mustAuthorize: Boolean = true)(implicit logger: IndentedLogger) = {

            debug("processing route", Seq("route" → this.toString))

            // Make sure the request is authorized
            if (mustAuthorize)
                authorize(ec)

            // PipelineConfig is reusable, but PipelineProcessor is not
            val pipeline = new PipelineProcessor(pipelineConfig)

            // Provide matches input using a digest, because that's equivalent to how the PFC was working when the
            // matches were depending on oxf:request. If we don't do this, then
            val matchesProcessor = new DigestedProcessor(RegexpMatcher.writeXML(_, matchResult))

            // Connect matches input and start pipeline
            PipelineUtils.connect(matchesProcessor, "data", pipeline, "matches")

            matchesProcessor.reset(pc)
            pipeline.reset(pc)
            pipeline.start(pc)
        }
    }

    trait Authorization {

        self: PageOrServiceRoute ⇒

        // Require authorization based on whether the request method is considered publicly accessible or not. If it is
        // public, then authorization does not take place.
        def requireAuthorization(request: Request) =
            ! routeElement.publicMethods(request.getMethod)

        // Authorize the incoming request. Throw an HttpStatusCodeException if the request requires authorization based
        // on the request method, and is not authorized (with a token or via an authorizer service).
        def authorize(ec: ExternalContext)(implicit logger: IndentedLogger) =
            if (requireAuthorization(ec.getRequest) && ! Authorizer.authorized(ec))
                unauthorized()
    }

    def unauthorized() = throw new HttpStatusCodeException(403)

    case class PageFlow(
        routes: Seq[Route],
        notFoundRoute: Option[PageOrServiceRoute],
        unauthorizedRoute: Option[PageOrServiceRoute],
        errorRoute: Option[PageOrServiceRoute],
        pathMatchers: Seq[PathMatcher],
        file: Option[String])

    def att(e: Element, name: String) = Option(e.attributeValue(name))
    def idAtt(e: Element) = att(e, "id")

    // @path-info for backward compatibility
    def getPath(e: Element) = att(e, "path") orElse att(e, "path-info") ensuring (_.isDefined) get

    // Support "regexp" and "oxf:perl5-matcher" for backward compatibility
    val RegexpQNames = Set(new QName("regexp"), new QName("perl5-matcher", OXF_PROCESSORS_NAMESPACE))

    // Compile and convert glob expression if needed
    def compilePattern(e: Element, path: String, default: QName) =
        RegexpMatcher.compilePattern(path, glob = ! RegexpQNames(Option(extractAttributeValueQName(e, MatcherProperty)) getOrElse default))
}
