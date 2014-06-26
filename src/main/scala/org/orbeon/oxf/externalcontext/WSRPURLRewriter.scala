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
package org.orbeon.oxf.externalcontext

import URLRewriter._
import WSRPURLRewriter._
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.{List ⇒ JList}
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.URLRewriterUtils
import org.orbeon.oxf.xforms.processor.XFormsResourceServer.DynamicResourcesPath

// This URL rewriter rewrites URLs using the WSRP encoding
class WSRPURLRewriter(
        retrievePathMatchers: ⇒ JList[URLRewriterUtils.PathMatcher],
        request: ExternalContext.Request,
        wsrpEncodeResources: Boolean)
    extends URLRewriter {

    // We don't initialize the matchers right away, because when the rewriter is created, they may not be available.
    // Specifically. the rewriter is typically created along the ExternalContext and PipelineContext, before the PFC has
    // been able to place the matchers in the PipelineContext.
    private var pathMatchers: JList[URLRewriterUtils.PathMatcher] = null

    // For Java callers, use Callable
    def this(getPathMatchers: Callable[JList[URLRewriterUtils.PathMatcher]], request: ExternalContext.Request, wsrpEncodeResources: Boolean) =
        this(getPathMatchers.call, request, wsrpEncodeResources)

    private def getPathMatchers = {
        if (pathMatchers eq null)
            pathMatchers = Option(retrievePathMatchers) getOrElse URLRewriterUtils.EMPTY_PATH_MATCHER_LIST

        pathMatchers
    }

    def rewriteRenderURL(urlString: String) =
        rewritePortletURL(urlString, URLTypeRender, null, null)

    def rewriteRenderURL(urlString: String, portletMode: String, windowState: String) =
        rewritePortletURL(urlString, URLTypeRender, portletMode, windowState)

    def rewriteActionURL(urlString: String) =
        rewritePortletURL(urlString, URLTypeBlockingAction, null, null)

    def rewriteActionURL(urlString: String, portletMode: String, windowState: String) =
        rewritePortletURL(urlString, URLTypeBlockingAction, portletMode, windowState)

    def rewriteResourceURL(urlString: String, rewriteMode: Int) =
        rewriteResourceURL(urlString, wsrpEncodeResources) // the mode is ignored

    def getNamespacePrefix = PrefixTag

    private def rewritePortletURL(urlString: String, urlType: Int, portletMode: String, windowState: String): String = {
        // Case where a protocol is specified OR it's just a fragment: the URL is left untouched
        if (NetUtils.urlHasProtocol(urlString) || urlString.startsWith("#"))
            return urlString

        // TEMP HACK to avoid multiple rewrites
        // TODO: Find out where it happens. Check XFOutputControl with image mediatype for example.
        if (urlString.indexOf("wsrp_rewrite") != -1)
            return urlString

        // Parse URL
        val baseURL = new URL("http", "example.org", request.getRequestPath)
        val u = new URL(baseURL, urlString)
        // Decode query string
        val parameters = NetUtils.decodeQueryStringPortlet(u.getQuery)
        // Add special path parameter
        val path =
            if (urlString.startsWith("?"))
                // This is a special case that appears to be implemented
                // in Web browsers as a convenience. Users may use it.
                request.getRequestPath
            else
                // Regular case, use parsed path
                URLRewriterUtils.getRewritingContext("wsrp", "") + u.getPath

        parameters.put(PathParameterName, Array(path))

        // Encode as "navigational state"
        val navigationalState = NetUtils.encodeQueryString2(parameters)

        // Encode the URL a la WSRP
        encodePortletURL(urlType, navigationalState, portletMode, windowState, u.getRef, secure = false)
    }

    def rewriteResourceURL(urlString: String, wsrpEncodeResources: Boolean): String = {
        // Always encode dynamic resources
        if (wsrpEncodeResources || urlString == "/xforms-server" || urlString.startsWith(DynamicResourcesPath)) {
            // First rewrite path to support versioned resources
            val rewrittenPath = URLRewriterUtils.rewriteResourceURL(request, urlString, getPathMatchers, REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT)

            // Then do the WSRP encoding
            rewritePortletURL(rewrittenPath, URLTypeResource, null, null)
        } else
            // Generate resource served by the servlet
            URLRewriterUtils.rewriteResourceURL(request, urlString, getPathMatchers, REWRITE_MODE_ABSOLUTE_PATH)
    }
}

object WSRPURLRewriter {

    val PathParameterName = "orbeon.path"

    private val URLTypeBlockingAction = 1
    private val URLTypeRender = 2
    private val URLTypeResource = 3

    val URLTypeBlockingActionString = "blockingAction"
    val URLTypeRenderString = "render"
    val URLTypeResourceString = "resource"

    private val URLTypes = Map(
        URLTypeBlockingAction   → URLTypeBlockingActionString,
        URLTypeRender           → URLTypeRenderString,
        URLTypeResource         → URLTypeResourceString
    )

    val BaseTag     = "wsrp_rewrite"
    val StartTag    = BaseTag + '?'
    val EndTag      = '/' + BaseTag
    val PrefixTag   = BaseTag + '_'

    val URLTypeParam = "wsrp-urlType"
    val ModeParam = "wsrp-mode"
    val WindowStateParam = "wsrp-windowState"
    val NavigationalStateParam = "wsrp-navigationalState"

    val BaseTagLength   = BaseTag.length
    val StartTagLength  = StartTag.length
    val EndTagLength    = EndTag.length
    val PrefixTagLength = PrefixTag.length

    /**
     * Encode an URL into a WSRP pattern including the string "wsrp_rewrite".
     *
     * This does not call the portlet API. Used by Portlet2URLRewriter.
     */
    def encodePortletURL(urlType: Int, navigationalState: String, mode: String, windowState: String, fragmentId: String, secure: Boolean): String = {

        val sb = new StringBuilder(StartTag)

        sb.append(URLTypeParam)
        sb.append('=')

        val urlTypeString = URLTypes.getOrElse(urlType, throw new IllegalArgumentException)

        sb.append(urlTypeString)

        // Encode mode
        if (mode ne null) {
            sb.append('&')
            sb.append(ModeParam)
            sb.append('=')
            sb.append(mode)
        }

        // Encode window state
        if (windowState ne null) {
            sb.append('&')
            sb.append(WindowStateParam)
            sb.append('=')
            sb.append(windowState)
        }

        // Encode navigational state
        if (navigationalState ne null) {
            sb.append('&')
            sb.append(NavigationalStateParam)
            sb.append('=')
            sb.append(URLEncoder.encode(navigationalState, "utf-8"))
        }
        sb.append(EndTag)

        sb.toString
    }
}