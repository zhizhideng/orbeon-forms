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
package org.orbeon.oxf.xforms.processor.handlers.xml;

import org.orbeon.oxf.xforms.StaticStateGlobalOps;
import org.orbeon.oxf.xforms.analysis.ElementAnalysis;
import org.orbeon.oxf.xforms.analysis.controls.StaticLHHASupport;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.processor.handlers.XFormsControlLifecycleHandlerDelegate;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * This class is a helper base class which handles the lifecycle of producing markup for a control. The following
 * phases are handled:
 *
 * o Give the handler a chance to do some prep work: prepareHandler()
 * o Check whether the control wants any output at all: isMustOutputControl()
 * o Output label, control, hint, help, and alert in order specified by properties
 *
 * Outputting the control is split into two parts: handleControlStart() and handleControlEnd().
 */
public class XFormsControlLifecyleHandlerXML extends XFormsBaseHandlerXML {

	private XFormsControlLifecycleHandlerDelegate xFormsControlLifecycleHandlerDelegate;
	private Attributes attributes;

    protected XFormsControlLifecyleHandlerXML(boolean repeating) {
        super(repeating, false);
    }

    protected XFormsControlLifecyleHandlerXML(boolean repeating, boolean forwarding) {
        super(repeating, forwarding);
    }

    
	protected boolean isTemplate() {
		return xFormsControlLifecycleHandlerDelegate.isTemplate();
	}

	protected String getPrefixedId() {
		return xFormsControlLifecycleHandlerDelegate.prefixedId();
	}

	protected String getEffectiveId() {
		return xFormsControlLifecycleHandlerDelegate.effectiveId();
	}

	protected XFormsControl currentControlOrNull() {
		return xFormsControlLifecycleHandlerDelegate.currentControlOrNull();
	}

    @Override
    public void init(String uri, String localname, String qName, Attributes attributes, Object matched) throws SAXException {
        super.init(uri, localname, qName, attributes, matched);
        this.attributes = new AttributesImpl(attributes);
        this.xFormsControlLifecycleHandlerDelegate = new XFormsControlLifecycleHandlerDelegate(handlerContext, containingDocument, attributes);
    }

    @Override
    public final void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (isMustOutputControl(currentControlOrNull())) {

            handleControlStart(uri, localname, qName, attributes, getEffectiveId(), currentControlOrNull());
            
            // xf:label
            if (hasLocalLabel())
                handleLabel();
            
            // xf:help
            if (hasLocalHelp())
                handleHelp();

            // xf:hint
            if (hasLocalHint())
                handleHint();
            
            // xf:alert
            if (hasLocalAlert())
                handleAlert();
        }
    }

    @Override
    public final void end(String uri, String localname, String qName) throws SAXException {
        if (isMustOutputControl(currentControlOrNull())) {
        	handleControlEnd(uri, localname, qName, attributes, getEffectiveId(), currentControlOrNull());
        }
    }

    private boolean hasLocalLabel() {
        return hasLocalLHHA("label");
    }

    private boolean hasLocalHint() {
        return hasLocalLHHA("hint");
    }

    private boolean hasLocalHelp() {
        return hasLocalLHHA("help");
    }

    private boolean hasLocalAlert() {
        return hasLocalLHHA("alert");
    }

    private boolean hasLocalLHHA(String lhhaType) {
        final StaticStateGlobalOps globalOps = containingDocument.getStaticOps();
        final ElementAnalysis analysis = globalOps.getControlAnalysis(getPrefixedId());
        if (analysis instanceof StaticLHHASupport)
            return ((StaticLHHASupport) analysis).hasLocal(lhhaType);
        else
            return false;
    }

    protected boolean isMustOutputControl(XFormsControl control) {
        // May be overridden by subclasses
        return true;
    }

    protected void handleLabel() throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(getEffectiveId(), getForEffectiveId(), LHHAC.LABEL, currentControlOrNull(), isTemplate());
    }

    protected void handleAlert() throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(getEffectiveId(), getForEffectiveId(), LHHAC.ALERT, currentControlOrNull(), isTemplate());
    }

    protected void handleHint() throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(getEffectiveId(), getForEffectiveId(), LHHAC.HINT, currentControlOrNull(), isTemplate());
    }

    protected void handleHelp() throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(getEffectiveId(), getForEffectiveId(), LHHAC.HELP, currentControlOrNull(), isTemplate());
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String effectiveId, XFormsControl control) throws SAXException {
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        
        reusableAttributes.clear();
        reusableAttributes.setAttributes(attributes);
		updateID(reusableAttributes, effectiveId, LHHAC.CONTROL);
		handleMIPAttributes(reusableAttributes, getPrefixedId(), control);
        handleValueAttribute(reusableAttributes, getPrefixedId(), control);
        handleExtraAttributesForControlStart(reusableAttributes, effectiveId, control);
        
        
        contentHandler.startElement(uri, localname, qName, reusableAttributes);
    }

	protected void handleExtraAttributesForControlStart(AttributesImpl reusableAttributes, String prefixedId, XFormsControl control) { }

	protected void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String effectiveId, XFormsControl control) throws SAXException {
    	 final ContentHandler contentHandler = handlerContext.getController().getOutput();
    	 
    	 contentHandler.endElement(uri, localname, qName);
    }

    /**
     * Return the effective id of the element to which label/@for, etc. must point to.
     *
     * @return                      @for effective id
     */
    public String getForEffectiveId() {
        return getEffectiveId();
    }
}
