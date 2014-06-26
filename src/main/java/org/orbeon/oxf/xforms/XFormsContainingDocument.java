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
package org.orbeon.oxf.xforms;

import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.OrbeonLocationException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.action.XFormsAPI;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.control.Controls;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary;
import org.orbeon.oxf.xforms.processor.XFormsURIResolver;
import org.orbeon.oxf.xforms.script.ScriptInterpreter;
import org.orbeon.oxf.xforms.state.*;
import org.orbeon.oxf.xforms.submission.AsynchronousSubmissionManager;
import org.orbeon.oxf.xforms.submission.SubmissionResult;
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.oxf.xml.XMLReceiverHelper;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.saxon.functions.FunctionLibrary;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;

/**
 * Represents an XForms containing document.
 *
 * The containing document:
 *
 * o Is the container for root XForms models (including multiple instances)
 * o Contains XForms controls
 * o Handles event handlers hierarchy
 */
public class XFormsContainingDocument extends XFormsContainingDocumentBase {

    private String uuid;        // UUID of this document
    private long sequence = 1;  // sequence number of changes to this document

    private SAXStore lastAjaxResponse; // last Ajax response for retry feature

    // Global XForms function library
    private static FunctionLibrary functionLibrary = XFormsFunctionLibrary.instance();

    // Whether the document supports updates
    private final boolean supportUpdates;

    // Whether this document is currently being initialized
    private boolean initializing;

    // Transient URI resolver for initialization
    private XFormsURIResolver uriResolver;

    // Transient OutputStream for xf:submission[@replace = 'all'], or null if not available
    private ExternalContext.Response response;

    // Asynchronous submission manager
    private AsynchronousSubmissionManager asynchronousSubmissionManager;

    // Interpreter for JavaScript, etc.
    private ScriptInterpreter scriptInterpreter;

    // A document refers to the static state and controls
    private final XFormsStaticState staticState;
    private final StaticStateGlobalOps staticOps;
    private XFormsControls xformsControls;


    // Other state
    private Set<String> pendingUploads;

    // Client state
    private XFormsModelSubmission activeSubmissionFirstPass;
    private Callable<SubmissionResult> replaceAllCallable;
    private boolean gotSubmissionReplaceAll;
    private boolean gotSubmissionRedirect;
    private List<Message> messagesToRun;
    private List<Load> loadsToRun;
    private List<Script> scriptsToRun;
    private String helpEffectiveControlId;
    private List<DelayedEvent> delayedEvents;
    private List<XFormsError.ServerError> serverErrors;
    private Set<String> controlsStructuralChanges;

    private final XPathDependencies xpathDependencies;

    /**
     * Return the global function library.
     */
    public static FunctionLibrary getFunctionLibrary() {
        return functionLibrary;
    }

    /**
     * Create an XFormsContainingDocument from an XFormsStaticState object.
     *
     * Used by XFormsToXHTML.
     *
     * @param staticState static state object
     * @param uriResolver URIResolver for loading instances during initialization (and possibly more, such as schemas and "GET" submissions upon initialization)
     * @param response    optional response for handling replace="all" during initialization
     * @param initialize  initialize document (false for testing only)
     */
    public XFormsContainingDocument(XFormsStaticState staticState, XFormsURIResolver uriResolver, ExternalContext.Response response, boolean initialize) {
        super();

        // Create UUID for this document instance
        this.uuid = SecureUtils.randomHexId();

        // Initialize request information
        {
            initializeRequestInformation();
            initializePathMatchers();
        }

        indentedLogger().startHandleOperation("initialization", "creating new ContainingDocument (static state object provided).", "uuid", this.uuid);
        {
            // Remember static state
            this.staticState = staticState;
            this.staticOps = new StaticStateGlobalOps(staticState.topLevelPart());

            // NOTE: template is not stored right away, as it depends on the evaluation of the noscript property.

            this.xpathDependencies = Version.instance().createUIDependencies(this);

            // Whether we support updates
            // NOTE: Reading the property requires the static state set above
            this.supportUpdates = ! isNoUpdates();

            // Remember parameters used during initialization
            this.uriResolver = uriResolver;
            this.response = response;
            this.initializing = true;

            // Initialize the containing document
            if (initialize) {
                try {
                    initialize();
                } catch (Exception e) {
                    throw OrbeonLocationException.wrapException(e, new ExtendedLocationData(null, "initializing XForms containing document"));
                }
            }
        }
        indentedLogger().endHandleOperation();
    }

    // This is called upon the first creation of the XForms engine
    private void initialize() {

        // Scope the containing document for the XForms API
        XFormsAPI.withContainingDocumentJava(this, new Runnable() {
            public void run() {
                // Create XForms controls and models
                createControlsAndModels();

                // Group all xforms-model-construct-done and xforms-ready events within a single outermost action handler in
                // order to optimize events
                // Perform deferred updates only for xforms-ready
                startOutermostActionHandler();
                {
                    // Initialize models
                    initializeModels();

                    // After initialization, some async submissions might be running
                    processCompletedAsynchronousSubmissions(true, true);
                }
                // End deferred behavior
                endOutermostActionHandler();
            }
        });
    }

    /**
     * Restore an XFormsContainingDocument from XFormsState only.
     *
     * Used by XFormsStateManager.
     *
     * @param xformsState       XFormsState containing static and dynamic state
     * @param disableUpdates    whether to disable updates (for recreating initial document upon browser back)
     */
    public XFormsContainingDocument(XFormsState xformsState, boolean disableUpdates) {
        super();

        // 1. Restore the static state
        {
            final scala.Option<String> staticStateDigest = xformsState.staticStateDigest();

            if (staticStateDigest.isDefined()) {
                final XFormsStaticState cachedState = XFormsStaticStateCache.getDocumentJava(staticStateDigest.get());
                if (cachedState != null) {
                    // Found static state in cache
                    indentedLogger().logDebug("", "found static state by digest in cache");
                    this.staticState = cachedState;
                } else {
                    // Not found static state in cache, create static state from input
                    indentedLogger().logDebug("", "did not find static state by digest in cache");
                    indentedLogger().startHandleOperation("initialization", "restoring static state");
                    this.staticState = XFormsStaticStateImpl.restore(staticStateDigest, xformsState.staticState());
                    indentedLogger().endHandleOperation();

                    // Store in cache
                    XFormsStaticStateCache.storeDocument(this.staticState);
                }

                assert this.staticState.isServerStateHandling();
            } else {
                // Not digest provided, create static state from input
                indentedLogger().logDebug("", "did not find static state by digest in cache");
                this.staticState = XFormsStaticStateImpl.restore(staticStateDigest, xformsState.staticState());

                assert this.staticState.isClientStateHandling();
            }

            this.staticOps = new StaticStateGlobalOps(staticState.topLevelPart());
            this.xpathDependencies = Version.instance().createUIDependencies(this);

            this.supportUpdates = ! disableUpdates && ! isNoUpdates();
        }

        // 2. Restore the dynamic state
        indentedLogger().startHandleOperation("initialization", "restoring containing document");
        try {
            restoreDynamicState(xformsState.dynamicState());
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, new ExtendedLocationData(null, "re-initializing XForms containing document"));
        }
        indentedLogger().endHandleOperation();
    }

    private void restoreDynamicState(final DynamicState dynamicState) {

        this.uuid = dynamicState.uuid();
        this.sequence = dynamicState.sequence();

        indentedLogger().logDebug("initialization", "restoring dynamic state for UUID", "UUID", this.uuid, "sequence", Long.toString(this.sequence));

        // Restore request information
        restoreRequestInformation(dynamicState);
        restorePathMatchers(dynamicState);
        restoreTemplate(dynamicState);

        // Restore other encoded objects
        this.pendingUploads = new HashSet<String>(dynamicState.decodePendingUploadsJava()); // make copy as must be mutable
        this.lastAjaxResponse = dynamicState.decodeLastAjaxResponseJava();

        // Scope the containing document for the XForms API
        XFormsAPI.withContainingDocumentJava(this, new Runnable() {
            public void run() {
                Controls.withDynamicStateToRestoreJava(dynamicState.decodeInstancesControls(), new Runnable() {
                    public void run() {
                        // Restore models state
                        // Create XForms controls and models
                        createControlsAndModels();

                        // Restore top-level models state, including instances
                        restoreModelsState();

                        // Restore controls state
                        // Store serialized control state for retrieval later
                        xformsControls.createControlTree();

                        // Once the control tree is rebuilt, restore focus if needed
                        if (dynamicState.decodeFocusedControlJava() != null)
                            xformsControls.setFocusedControl(xformsControls.getCurrentControlTree().getControl(dynamicState.decodeFocusedControlJava()));
                    }
                });
            }
        });
    }

    @Override
    public PartAnalysis partAnalysis() {
        return staticState.topLevelPart();
    }

    public XFormsURIResolver getURIResolver() {
        return uriResolver;
    }

    public String getUUID() {
        return uuid;
    }

    public void updateChangeSequence() {
        sequence++;
    }

    public SAXStore getLastAjaxResponse() {
        return lastAjaxResponse;
    }

    public boolean isInitializing() {
        return initializing;
    }

    /**
     * Whether the document is currently in a mode where it must remember differences. This is the case when:
     *
     * - the document is currently handling an update (as opposed to initialization)
     * - the property "no-updates" is false (the default)
     * - the document is
     *
     * @return  true iif the document must handle differences
     */
    public boolean isHandleDifferences() {
        return ! initializing && supportUpdates;
    }

    /**
     * Return the controls.
     */
    public XFormsControls getControls() {
        return xformsControls;
    }

    public XFormsControl getControlByEffectiveId(String effectiveId) {
        return xformsControls.getObjectByEffectiveId(effectiveId);
    }

    /**
     * Return dependencies implementation.
     */
    public final XPathDependencies getXPathDependencies() {
        return xpathDependencies;
    }

    /**
     * Whether the document is dirty since the last request.
     *
     * @return  whether the document is dirty since the last request
     */
    public boolean isDirtySinceLastRequest() {
        return xformsControls.isDirtySinceLastRequest();
    }
    
    /**
     * Return the static state of this document.
     */
    public XFormsStaticState getStaticState() {
        return staticState;
    }

    public StaticStateGlobalOps getStaticOps() {
        return staticOps;
    }

    /**
     * Get object with the effective id specified.
     *
     * @param effectiveId   effective id of the target
     * @return              object, or null if not found
     */
    public XFormsObject getObjectByEffectiveId(String effectiveId) {

        // Search in controls first because that's the fast way
        {
            final XFormsObject resultObject = getControlByEffectiveId(effectiveId);
            if (resultObject != null)
                return resultObject;
        }

        // Search in parent (models and this)
        {
            final XFormsObject resultObject = super.getObjectByEffectiveId(effectiveId);
            if (resultObject != null)
                return resultObject;
        }

        // Check container id
        // TODO: This should no longer be needed since we have a root control, right? In which case, the document would
        // no longer need to be an XFormsObject.
        if (effectiveId.equals(getEffectiveId()))
            return this;

        return null;
    }

    /**
     * Return the active submission if any or null.
     */
    public XFormsModelSubmission getClientActiveSubmissionFirstPass() {
        return activeSubmissionFirstPass;
    }

    public Callable<SubmissionResult> getReplaceAllCallable() {
        return replaceAllCallable;
    }

    /**
     * Clear current client state.
     */
    private void clearClientState() {

        assert !initializing;
        assert response == null;
        assert uriResolver == null;

        this.activeSubmissionFirstPass = null;
        this.replaceAllCallable = null;
        this.gotSubmissionReplaceAll = false;
        this.gotSubmissionRedirect = false;

        this.messagesToRun = null;
        this.loadsToRun = null;
        this.scriptsToRun = null;
        this.helpEffectiveControlId = null;
        this.delayedEvents = null;
        
        this.serverErrors = null;

        clearRequestStats();

        if (this.controlsStructuralChanges != null)
            this.controlsStructuralChanges.clear();
    }

    /**
     * Add a two-pass submission.
     *
     * This can be called with a non-null value at most once.
     */
    public void setActiveSubmissionFirstPass(XFormsModelSubmission submission) {
        if (this.activeSubmissionFirstPass != null)
            throw new ValidationException("There is already an active submission.", submission.getLocationData());

        if (loadsToRun != null)
            throw new ValidationException("Unable to run a two-pass submission and xf:load within a same action sequence.", submission.getLocationData());

        // NOTE: It seems reasonable to run scripts, messages, focus, and help up to the point where the submission takes place.

        // Remember submission
        this.activeSubmissionFirstPass = submission;
    }

    public void setReplaceAllCallable(Callable<SubmissionResult> callable) {
        this.replaceAllCallable = callable;
    }

    public void setGotSubmission() {}

    public void setGotSubmissionReplaceAll() {
        if (this.gotSubmissionReplaceAll)
            throw new OXFException("Unable to run a second submission with replace=\"all\" within a same action sequence.");

        this.gotSubmissionReplaceAll = true;
    }

    public boolean isGotSubmissionReplaceAll() {
        return gotSubmissionReplaceAll;
    }

    public void setGotSubmissionRedirect() {
        if (this.gotSubmissionRedirect)
            throw new OXFException("Unable to run a second submission with replace=\"all\" redirection within a same action sequence.");

        this.gotSubmissionRedirect = true;
    }

    public boolean isGotSubmissionRedirect() {
        return gotSubmissionRedirect;
    }

    /**
     * Add an XForms message to send to the client.
     */
    public void addMessageToRun(String message, String level) {
        if (messagesToRun == null)
            messagesToRun = new ArrayList<Message>();
        messagesToRun.add(new Message(message, level));
    }

    /**
     * Return the list of messages to send to the client, null if none.
     */
    public List<Message> getMessagesToRun() {
        if (messagesToRun != null)
            return messagesToRun;
        else
            return Collections.emptyList();
    }

    /**
     * Schedule an event for delayed execution, following xf:dispatch/@delay semantics.
     *
     * @param eventName         name of the event to dispatch
     * @param targetEffectiveId id of the target to dispatch to
     * @param bubbles           whether the event bubbles
     * @param cancelable        whether the event is cancelable
     * @param delay             delay after which to dispatch the event
     * @param isMaxDelay        whether the delay indicates a maximum delay
     * @param showProgress      whether to show the progress indicator when submitting the event
     * @param progressMessage   message to show if the progress indicator is visible
     */
    public void addDelayedEvent(String eventName, String targetEffectiveId, boolean bubbles, boolean cancelable, int delay,
                                boolean isMaxDelay, boolean showProgress, String progressMessage) {
        if (delayedEvents == null)
            delayedEvents = new ArrayList<DelayedEvent>();

        delayedEvents.add(new DelayedEvent(eventName, targetEffectiveId, bubbles, cancelable, System.currentTimeMillis() + delay,
                isMaxDelay, showProgress, progressMessage));
    }

    public List<DelayedEvent> getDelayedEvents() {
        if (delayedEvents != null)
            return delayedEvents;
        else
            return Collections.emptyList();
    }

    public static class DelayedEvent {
        // Event information
        private final String eventName;
        private final String targetEffectiveId;
        private final boolean bubbles;
        private final boolean cancelable;
        // Meta information
        private final long time;
        private final boolean isMaxDelay;
        private final boolean showProgress;
        private final String progressMessage;

        public DelayedEvent(String eventName, String targetEffectiveId, boolean bubbles, boolean cancelable, long time,
                            boolean isMaxDelay, boolean showProgress, String progressMessage) {
            this.eventName = eventName;
            this.targetEffectiveId = targetEffectiveId;
            this.bubbles = bubbles;
            this.cancelable = cancelable;
            this.time = time;
            this.isMaxDelay = isMaxDelay;
            this.showProgress = showProgress;
            this.progressMessage = progressMessage;
        }

        public String getEncodedDocument() {
            final Document eventsDocument = Dom4jUtils.createDocument();
            final Element eventsElement = eventsDocument.addElement(XFormsConstants.XXFORMS_EVENTS_QNAME);

            final Element eventElement = eventsElement.addElement(XFormsConstants.XXFORMS_EVENT_QNAME);
            eventElement.addAttribute("name", eventName);
            eventElement.addAttribute("source-control-id", targetEffectiveId);
            eventElement.addAttribute("bubbles", Boolean.toString(bubbles));
            eventElement.addAttribute("cancelable", Boolean.toString(cancelable));

            return XFormsUtils.encodeXML(eventsDocument, false);
        }

        public boolean isShowProgress() {
            return showProgress;
        }

        public String getProgressMessage() {
            return progressMessage;
        }

        public long getTime() {
            return time;
        }

        public boolean isMaxDelay() {
            return isMaxDelay;
        }

        public void toSAX(XMLReceiverHelper ch, long currentTime) {
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "server-events",
                    new String[] {
                            "delay", Long.toString(getTime() - currentTime),
                            "discardable", isMaxDelay() ? "true" : null,
                            "show-progress", Boolean.toString(isShowProgress()),
                            "progress-message", isShowProgress() ? getProgressMessage() : null
                    });
            ch.text(getEncodedDocument());
            ch.endElement();
        }

        public void toJSON(StringBuilder sb, long currentTime) {
            sb.append('{');
            sb.append("\"delay\":");
            sb.append(getTime() - currentTime);
            if (isMaxDelay()) {
                sb.append(",\"discardable\":true");
            }
            sb.append(",\"show-progress\":");
            sb.append(isShowProgress());
            if (isShowProgress()) {
                sb.append(",\"progress-message\":\"");
                XFormsUtils.escapeJavaScript(getProgressMessage());
                sb.append('"');
            }
            sb.append(",\"event\":\"");
            sb.append(getEncodedDocument());
            sb.append('"');

            sb.append("}");
        }
    }

    public static class Message {
        private String message;
        private String level;

        public Message(String message, String level) {
            this.message = message;
            this.level = level;
        }

        public String getMessage() {
            return message;
        }

        public String getLevel() {
            return level;
        }
    }

    public void addScriptToRun(org.orbeon.oxf.xforms.Script script, XFormsEvent event, XFormsEventObserver eventObserver) {

        if (activeSubmissionFirstPass != null && StringUtils.isBlank(activeSubmissionFirstPass.getResolvedXXFormsTarget())) {
            // Scripts occurring after a submission without a target takes place should not run
            // TODO: Should we allow scripts anyway? Don't we allow value changes updates on the client anyway?
            indentedLogger().logWarning("", "xxf:script will be ignored because two-pass submission started", "script id", script.prefixedId());
            return;
        }

        // Warn that scripts won't run in noscript mode (duh)
        if (noscript())
            indentedLogger().logWarning("noscript", "script won't run in noscript mode", "script id", script.prefixedId());

        if (scriptsToRun == null)
            scriptsToRun = new ArrayList<Script>();
        scriptsToRun.add(new Script(script.clientName(), event, eventObserver));
    }

    public static class Script {
        public final String functionName;
        public final String targetEffectiveId;
        public final String observerEffectiveId;

        public Script(String functionName, XFormsEvent event, XFormsEventObserver eventObserver) {
            this.functionName = functionName;
            this.targetEffectiveId = event.targetObject().getEffectiveId();
            this.observerEffectiveId = eventObserver.getEffectiveId();
        }
    }

    public List<Script> getScriptsToRun() {
        if (scriptsToRun != null)
            return scriptsToRun;
        else
            return Collections.emptyList();
    }

    /**
     * Add an XForms load to send to the client.
     */
    public void addLoadToRun(String resource, String target, String urlType, boolean isReplace, boolean isShowProgress) {

        if (activeSubmissionFirstPass != null)
            throw new ValidationException("Unable to run a two-pass submission and xf:load within a same action sequence.", activeSubmissionFirstPass.getLocationData());

        if (loadsToRun == null)
            loadsToRun = new ArrayList<Load>();
        loadsToRun.add(new Load(resource, target, urlType, isReplace, isShowProgress));
    }

    /**
     * Return the list of loads to send to the client, null if none.
     */
    public List<Load> getLoadsToRun() {
        if (loadsToRun != null)
            return loadsToRun;
        else
            return Collections.emptyList();
    }

    public static class Load {
        private final String resource;
        private final String target;
        private final String urlType;
        private final boolean isReplace;
        private final boolean isShowProgress;

        public Load(String resource, String target, String urlType, boolean isReplace, boolean isShowProgress) {
            this.resource = resource;
            this.target = target;
            this.urlType = urlType;
            this.isReplace = isReplace;
            this.isShowProgress = isShowProgress;
        }

        public String getResource() {
            return resource;
        }

        public String getTarget() {
            return target;
        }

        public String getUrlType() {
            return urlType;
        }

        public boolean isReplace() {
            return isReplace;
        }

        public boolean isShowProgress() {
            return isShowProgress;
        }
    }

    /**
     * Tell the client that help must be shown for the given effective control id.
     *
     * This can be called several times, but only the last control id is remembered.
     *
     * @param effectiveControlId
     */
    public void setClientHelpEffectiveControlId(String effectiveControlId) {
        this.helpEffectiveControlId = effectiveControlId;
    }

    /**
     * Return the effective control id of the control to help for, or null.
     */
    public String getClientHelpControlEffectiveId() {

        if (helpEffectiveControlId == null)
            return null;

        final XFormsControl xformsControl = getControlByEffectiveId(helpEffectiveControlId);
        // It doesn't make sense to tell the client to show help for an element that is non-relevant, but we allow readonly
        if (xformsControl != null && xformsControl instanceof XFormsSingleNodeControl) {
            final XFormsSingleNodeControl xformsSingleNodeControl = (XFormsSingleNodeControl) xformsControl;
            if (xformsSingleNodeControl.isRelevant())
                return helpEffectiveControlId;
            else
                return null;
        } else {
            return null;
        }
    }
    
    public void addServerError(XFormsError.ServerError serverError) {
        final int maxErrors = getShowMaxRecoverableErrors();
        if (maxErrors > 0) {
            if (serverErrors == null)
                serverErrors = new ArrayList<XFormsError.ServerError>();

            if (serverErrors.size() < maxErrors)
                serverErrors.add(serverError);
        }
    }
    
    public List<XFormsError.ServerError> getServerErrors() {
        return serverErrors != null ? serverErrors : Collections.<XFormsError.ServerError>emptyList();
    }

    public Set<String> getControlsStructuralChanges() {
        return controlsStructuralChanges != null ? controlsStructuralChanges : Collections.<String>emptySet();
    }

    public void addControlStructuralChange(String prefixedId) {
        if (this.controlsStructuralChanges == null)
            this.controlsStructuralChanges = new HashSet<String>();

        this.controlsStructuralChanges.add(prefixedId);
    }

    @Override
    public Scope innerScope() {
        // Do it here because at construction time, we don't yet have access to the static state!
        return staticState.topLevelPart().startScope();
    }

    /**
     * Check if there is a pending two-pass submission and run it if needed.
     *
     * This must NOT synchronize on this document and must NOT modify the document, because further Ajax request might
     * run concurrently on this document.
     *
     * @param callable          callable to run or null
     * @param response          response to write to if needed
     * @return                  true if calling the callable was successful
     */
    public static boolean checkAndRunDeferredSubmission(Callable<SubmissionResult> callable, ExternalContext.Response response) {
        if (callable != null) {
            XFormsModelSubmission.runDeferredSubmission(callable, response);
            return true;
        } else {
            return false;
        }
    }

    public void afterInitialResponse() {

        getRequestStats().afterInitialResponse();

        this.uriResolver = null;        // URI resolver is of no use after initialization and it may keep dangerous references (PipelineContext)
        this.response = null;           // same as above
        this.initializing = false;

        clearClientState(); // client state can contain e.g. focus information, etc. set during initialization

        // Tell dependencies
        xpathDependencies.afterInitialResponse();
    }

    /**
     * Prepare the document for a sequence of external events.
     *
     * @param response          ExternalContext.Response for xf:submission[@replace = 'all'], or null
     */
    public void beforeExternalEvents(ExternalContext.Response response) {

        // Tell dependencies
        xpathDependencies.beforeUpdateResponse();

        // Remember OutputStream
        this.response = response;

        // Process completed asynchronous submissions if any
        processCompletedAsynchronousSubmissions(false, false);
    }

    /**
     * End a sequence of external events.
     *
     */
    public void afterExternalEvents() {

        // Process completed asynchronous submissions if any
        processCompletedAsynchronousSubmissions(false, true);

        this.response = null;
    }

    /**
     * Called after sending a successful update response.
     */
    public void afterUpdateResponse() {

        getRequestStats().afterUpdateResponse();

        clearClientState();
        xformsControls.afterUpdateResponse();
        // Tell dependencies
        xpathDependencies.afterUpdateResponse();
    }

    public void rememberLastAjaxResponse(SAXStore response) {
        lastAjaxResponse = response;
    }

    public long getSequence() {
        return sequence;
    }

    /**
     * Return an OutputStream for xf:submission[@replace = 'all']. Used by submission.
     *
     * @return OutputStream
     */
    public ExternalContext.Response getResponse() {
        return response;
    }

    public AsynchronousSubmissionManager getAsynchronousSubmissionManager(boolean create) {
        if (asynchronousSubmissionManager == null && create)
            asynchronousSubmissionManager = new AsynchronousSubmissionManager(this);
        return asynchronousSubmissionManager;
    }

    private void processCompletedAsynchronousSubmissions(boolean skipDeferredEventHandling, boolean addPollEvent) {
        final AsynchronousSubmissionManager manager = getAsynchronousSubmissionManager(false);
        if (manager != null && manager.hasPendingAsynchronousSubmissions()) {
            if (!skipDeferredEventHandling)
                startOutermostActionHandler();
            manager.processCompletedAsynchronousSubmissions();
            if (!skipDeferredEventHandling)
                endOutermostActionHandler();

            // Remember to send a poll event if needed
            if (addPollEvent)
                manager.addClientDelayEventIfNeeded();
        }
    }

    public ScriptInterpreter getScriptInterpreter() {
        if (scriptInterpreter == null)
            scriptInterpreter = new ScriptInterpreter(this);
        return scriptInterpreter;
    }

    private void createControlsAndModels() {
        addAllModels();
        xformsControls = new XFormsControls(this);
    }

    public void initializeNestedControls() {
        // Call-back from super class models initialization

        // This is important because if controls use binds, those must be up to date. In addition, MIP values will be up
        // to date. Finally, upon receiving xforms-ready after initialization, it is better if calculations and
        // validations are up to date.
        rebuildRecalculateRevalidateIfNeeded();

        // Initialize controls
        xformsControls.createControlTree();
    }

    public Stack<XFormsEvent> eventStack = new Stack<XFormsEvent>();

    public void startHandleEvent(XFormsEvent event) {
        eventStack.push(event);
    }

    public void endHandleEvent() {
        eventStack.pop();
    }

    /**
     * Return the event being processed by the current event handler, null if no event is being processed.
     */
    public XFormsEvent getCurrentEvent() {
        return (eventStack.size() == 0) ? null : eventStack.peek();
    }

    @Override
    public List<XFormsControl> getChildrenControls(XFormsControls controls) {
        return controls.getCurrentControlTree().getChildren();
    }

    /**
     * Register that an upload has started.
     */
    public void startUpload(String uploadId) {
        if (pendingUploads == null)
            pendingUploads = new HashSet<String>();
        pendingUploads.add(uploadId);
    }

    /**
     * Register that an upload has ended.
     */
    public void endUpload(String uploadId) {
        // NOTE: Don't enforce existence of upload, as this is also called if upload control becomes non-relevant, and
        // also because asynchronously if the client notifies us to end an upload after a control has become non-relevant,
        // we don't want to fail.
        if (pendingUploads != null)
            pendingUploads.remove(uploadId);
    }

    public Set<String> getPendingUploads() {
        if (pendingUploads == null)
            return Collections.emptySet();
        else
            return pendingUploads;
    }

    /**
     * Return the number of pending uploads.
     */
    public int countPendingUploads() {
        return (pendingUploads == null) ? 0 : pendingUploads.size();
    }

    /**
     * Whether an upload is pending for the given upload control.
     */
    public boolean isUploadPendingFor(XFormsUploadControl uploadControl) {
        return (pendingUploads != null) && pendingUploads.contains(uploadControl.getUploadUniqueId());
    }

    /**
     * Called when this document is added to the document cache.
     */
    public void added() {
        XFormsStateManager.instance().onAddedToCache(getUUID());
    }

    /**
     * Called when somebody explicitly removes this document from the document cache.
     */
    public void removed() {
        // WARNING: This can be called while another threads owns this document lock
        XFormsStateManager.instance().onRemovedFromCache(getUUID());
    }

    /**
     * Called by the cache to check that we are ready to be evicted from cache.
     *
     * @return lock or null in case session just expired
     */
    public Lock getEvictionLock() {
        return XFormsStateManager.getDocumentLock(getUUID());
    }

    /**
     * Called when cache expires this document from the document cache.
     */
    public void evicted() {
        // WARNING: This could have been called while another threads owns this document lock, but the cache now obtains
        // the lock on the document first and will not evict us if we have the lock. This means that this will be called
        // only if no thread is dealing with this document.
        XFormsStateManager.instance().onEvictedFromCache(this);
    }
}
