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
package org.orbeon.oxf.fr.relational.crud

import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.webapp.HttpStatusCodeException
import org.orbeon.oxf.common.Version


class CRUD
        extends ProcessorImpl
        with RequestResponse
        with Common
        with Read
        with CreateUpdateDelete {

    override def start(pipelineContext: PipelineContext): Unit =
        try {
            val req = request

//            val PEProviders = Seq("oracle", "db2", "sqlserver")
//            if (PEProviders.contains(req.provider))
//                Version.instance.requirePEFeature("Enterprise relational database")

            httpRequest.getMethod match {
                case "GET"    ⇒ get(req)
                case "PUT"    ⇒ change(req, delete = false)
                case "DELETE" ⇒ change(req, delete = true)
                case _        ⇒ httpResponse.setStatus(405)
            }
        } catch {
            case e: HttpStatusCodeException ⇒
                httpResponse.setStatus(e.code)
        }
}