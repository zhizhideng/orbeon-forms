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
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControlBase;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.XMLConstants;
import scala.Tuple3;

/**
 * Represents an xf:range control.
 */
public class XFormsRangeControl extends XFormsValueControlBase {

    private String start;
    private String end;
    private String step;

    public XFormsRangeControl(XBLContainer container, XFormsControl parent, Element element, String id) {
        super(container, parent, element, id);
        this.start = element.attributeValue("start");
        this.end = element.attributeValue("end");
        this.step = element.attributeValue("step");
    }

    @Override
    public Tuple3<String, String, String> getJavaScriptInitialization() {
        return getCommonJavaScriptInitialization();
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }

    public String getStep() {
        return step;
    }

    @Override
    public String translateExternalValue(String externalValue) {
        return convertFromExternalValue(externalValue);
    }

    private String convertFromExternalValue(String externalValue) {
        final String typeName = getBuiltinTypeName();
        if (getStart() != null && getEnd() != null && "integer".equals(typeName)) {
            final int start = Integer.parseInt(getStart());
            final int end = Integer.parseInt(getEnd());

            final int value = start + ((int) (Double.parseDouble(externalValue) * (double) (end - start)));
            return Integer.toString(value);
        } else {
            return externalValue;
        }
    }

    @Override
    public void evaluateExternalValue() {
        final String internalValue = getValue();
        final String updatedValue;
        if (internalValue == null) {// can it be really?
            updatedValue = null;
        } else if (getStart() != null && getEnd() != null
                && (XMLConstants.XS_INTEGER_QNAME.equals(valueType()) || XFormsConstants.XFORMS_INTEGER_QNAME.equals(valueType()))) {

            final int start = Integer.parseInt(getStart());
            final int end = Integer.parseInt(getEnd());

            final double value = ((double) (Integer.parseInt(internalValue) - start)) / ((double) end - start);
            updatedValue = Double.toString(value);
        } else {
            updatedValue = internalValue;
        }
        setExternalValue(updatedValue);
    }
}
