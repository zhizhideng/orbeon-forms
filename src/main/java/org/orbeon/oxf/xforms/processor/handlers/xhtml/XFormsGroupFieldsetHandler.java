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
package org.orbeon.oxf.xforms.processor.handlers.xhtml;

import org.apache.commons.lang3.StringUtils;
import org.orbeon.oxf.xforms.control.LHHASupport;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsGroupControl;
import org.orbeon.oxf.xml.XMLReceiverHelper;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class XFormsGroupFieldsetHandler extends XFormsGroupHandler {

    @Override
    protected String getContainingElementName() {
        return "fieldset";
    }

    @Override
    public void handleControlStart(String uri, String localname, String qName, Attributes attributes, final String effectiveId, XFormsControl control) throws SAXException {

        final XFormsGroupControl groupControl = (XFormsGroupControl) control;
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final ElementHandlerController controller = handlerContext.getController();
        final ContentHandler contentHandler = controller.getOutput();

        // Output an xhtml:legend element if and only if there is an xf:label element. This help with
        // styling in particular.
        final boolean hasLabel = LHHASupport.hasLabel(containingDocument, getPrefixedId());
        if (hasLabel) {

            // Handle label classes
            reusableAttributes.clear();
            reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, getLabelClasses(groupControl));
            reusableAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, getLHHACId(containingDocument, effectiveId, LHHAC_CODES.get(LHHAC.LABEL)));

            // Output xhtml:legend with label content
            final String legendQName = XMLUtils.buildQName(xhtmlPrefix, "legend");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "legend", legendQName, reusableAttributes);
            {
                final String labelValue = getLabelValue(groupControl);
                if (StringUtils.isNotEmpty(labelValue))
                    contentHandler.characters(labelValue.toCharArray(), 0, labelValue.length());
            }
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "legend", legendQName);
        }
    }

    @Override
    protected void handleLabel() throws SAXException {
        // NOP because we handle the label in a custom way
    }
}
