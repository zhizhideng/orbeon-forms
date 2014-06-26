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
package org.orbeon.oxf.webapp

import collection.mutable.LinkedHashMap
import org.apache.log4j.Logger

class TestWebAppContext(private val logger: Logger) extends WebAppContext {
    def getResource(s: String) = throw new UnsupportedOperationException
    def getResourceAsStream(s: String) = throw new UnsupportedOperationException
    def getRealPath(s: String) = null
    val initParameters = Map.empty[String, String]
    val attributes = TestWebAppContext.attributes
    def log(message: String, throwable: Throwable) = logger.error(message, throwable)
    def log(message: String) = logger.info(message)
    def getNativeContext = null
}

object TestWebAppContext {
    // Make this static so that multiple TestExternalContext run in the same application scope. This is not 100% ideal
    // because it breaks test isolation.
    private val attributes = LinkedHashMap[String, AnyRef]()
}