/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.webapp

import org.orbeon.oxf.pipeline.InitUtils.runWithServletContext
import javax.servlet.{ServletException, ServletContextEvent, ServletContextListener}
import org.orbeon.oxf.util.ScalaUtils._

// For backward compatibility
class OrbeonServletContextListenerDelegate extends OrbeonServletContextListener

/**
 * This listener listens for HTTP context lifecycle changes.
 */
class OrbeonServletContextListener extends ServletContextListener {

    private val InitProcessorPrefix     = "oxf.context-initialized-processor."
    private val InitInputPrefix         = "oxf.context-initialized-processor.input."
    private val DestroyProcessorPrefix  = "oxf.context-destroyed-processor."
    private val DestroyInputPrefix      = "oxf.context-destroyed-processor.input."

    private implicit val logger = ProcessorService.Logger

    def logPrefix = "Context listener"
    def initParameters = Map()

    def contextInitialized(event: ServletContextEvent): Unit =
        withRootException("context creation", new ServletException(_)) {
            runWithServletContext(event.getServletContext, None, logger, logPrefix, "Context initialized.", InitProcessorPrefix, InitInputPrefix)
        }

    def contextDestroyed(event: ServletContextEvent): Unit =
        withRootException("context destruction", new ServletException(_)) {
            runWithServletContext(event.getServletContext, None, logger, logPrefix, "Context destroyed.", DestroyProcessorPrefix, DestroyInputPrefix)
            // NOTE: This calls all listeners, because the listeners are stored in the actual web app context's attributes
            WebAppContext(event.getServletContext).webAppDestroyed()
        }
}