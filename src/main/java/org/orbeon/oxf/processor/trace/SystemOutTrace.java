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
package org.orbeon.oxf.processor.trace;

import org.orbeon.oxf.xml.XMLReceiverHelper;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

public class SystemOutTrace extends BaseTrace {
    public void contextDestroyed(final boolean success) {
        if (traceEntries != null) {
            System.out.println(Dom4jUtils.domToPrettyString(XMLUtils.createDebugRequestDocument(new XMLUtils.DebugXML() {
                public void toXML(XMLReceiverHelper helper) {
                    buildTraceNodes(traceEntries.values()).toXML(pipelineContext, helper);
                }
            })));
        }
    }
}
