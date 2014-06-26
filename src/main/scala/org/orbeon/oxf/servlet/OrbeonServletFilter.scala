/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.servlet

import org.orbeon.oxf.pipeline.api._
import org.orbeon.oxf.processor.ServletFilterGenerator
import javax.servlet._
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import collection.JavaConverters._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.webapp.{WebAppContext, ProcessorService, ServletPortlet}

// For backward compatibility
class OrbeonServletFilterDelegate extends OrbeonServletFilter

class OrbeonServletFilter extends Filter with ServletPortlet {

    private implicit val logger = ProcessorService.Logger

    def logPrefix = "Servlet filter"

    // Immutable map of servlet parameters
    private var _initParameters: Map[String, String] = _
    def initParameters = _initParameters

    // Filter init
    def init(config: FilterConfig): Unit =
        withRootException("initialization", new ServletException(_)) {

            _initParameters =
                config.getInitParameterNames.asScala.asInstanceOf[Iterator[String]] map
                    (n ⇒ n → config.getInitParameter(n)) toMap

            init(WebAppContext(config.getServletContext), None)
        }

    // Filter destroy
    def destroy(): Unit =
        withRootException("destruction", new ServletException(_)) {
            destroy(None)
        }

    // Filter request
    def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit =
        withRootException("request", new ServletException(_)) {
            val pipelineContext = new PipelineContext
            pipelineContext.setAttribute(ServletFilterGenerator.FILTER_CHAIN, chain)
            val externalContext = new ServletExternalContext(pipelineContext, webAppContext, request.asInstanceOf[HttpServletRequest], response.asInstanceOf[HttpServletResponse])
            processorService.service(pipelineContext, externalContext)
        }
}