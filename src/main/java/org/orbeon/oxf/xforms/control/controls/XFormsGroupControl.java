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
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeContainerControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;

/**
 * Represents an xf:group container control.
 */
public class XFormsGroupControl extends XFormsSingleNodeContainerControl {

    public XFormsGroupControl(XBLContainer container, XFormsControl parent, Element element, String effectiveId) {
        super(container, parent, element, effectiveId);
    }

    @Override
    public boolean isStaticReadonly() {
        // Static readonly-ness doesn't seem to make much sense for xf:group, and we don't want to see the
        // xforms-static class in the resulting HTML
        return false;
    }

    @Override
    public boolean supportAjaxUpdates() {
        return !getAppearances().contains(XFormsConstants.XXFORMS_INTERNAL_APPEARANCE_QNAME);
    }

    @Override
    public QName valueType() {
        return null;
    }
}
