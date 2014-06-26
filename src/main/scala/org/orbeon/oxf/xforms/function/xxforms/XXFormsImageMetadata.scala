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
package org.orbeon.oxf.xforms.function.xxforms

import java.io.{InputStream, ByteArrayInputStream}
import org.apache.commons.codec.binary.Base64
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{ImageMetadata, NetUtils}
import org.orbeon.oxf.xforms.function.{FunctionSupport, XFormsFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.Item
import ImageMetadata._

class XXFormsImageMetadata extends XFormsFunction with FunctionSupport {

    override def evaluateItem(xpathContext: XPathContext): Item = {

        implicit val ctx = xpathContext

        def argumentAsString =
            nonEmptyOrNone(itemArgumentsOpt(0).get.next().getStringValue)

        def createStream(content: String) =
            if (NetUtils.urlHasProtocol(content))
                URLFactory.createURL(content).openStream()
            else
                new ByteArrayInputStream(Base64.decodeBase64(content))

        def findMetadata(is: InputStream) =
            stringArgument(1) match {
                case "mediatype" ⇒ findImageMediatype(is) map stringToStringValue
                case name        ⇒ findKnownMetadata(is, name)
            }

        argumentAsString map createStream flatMap findMetadata orNull
    }
}
