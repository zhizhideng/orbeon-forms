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
package org.orbeon.oxf.xforms.submission;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.log4j.Logger;
import org.dom4j.*;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.model.Instance;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.VirtualNode;

import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Represents an XForms model submission instance.
 *
 * TODO: Refactor handling of serialization to separate classes.
 */
public class XFormsModelSubmission extends XFormsModelSubmissionBase implements XFormsEventTarget, XFormsEventObserver {

    public static final String LOGGING_CATEGORY = "submission";
	public final static Logger logger = LoggerFactory.createLogger(XFormsModelSubmission.class);

    private final org.orbeon.oxf.xforms.analysis.model.Submission staticSubmission;
    private final String id;
    private final Element submissionElement;

    private final XBLContainer container;
    private final XFormsContainingDocument containingDocument;

    private final XFormsModel model;
    private boolean submissionElementExtracted = false;

    private String avtActionOrResource; // required unless there is a nested xf:resource element;
    private String avtMethod; // required

    private String avtValidate;
    private String avtRelevant;
    private String avtXXFormsCalculate;
    private String avtXXFormsUploads;
    private String avtXXFormsAnnotate;

    private String avtSerialization;

    private String targetref;// this is an XPath expression when used with replace="instance|text" (other meaning possible post-XForms 1.1 for replace="all")
    private String avtMode;

    private String avtVersion;
    private String avtEncoding;
    private String avtMediatype;
    private String avtIndent;
    private String avtOmitxmldeclaration;
    private String avtStandalone;
//    private String cdatasectionelements;

    private String replace = XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL;
    private String replaceInstanceId;
    private String xxfReplaceInstanceId;
    private String avtSeparator = "&";// XForms 1.1 changes back the default to the ampersand as of February 2009
//    private String includenamespaceprefixes;

    private String avtXXFormsUsername;
    private String avtXXFormsPassword;
    private String avtXXFormsPreemptiveAuthentication;
    private String avtXXFormsDomain;
    private String avtXXFormsReadonly;
    private String avtXXFormsShared;
    private String avtXXFormsCache;
    private String avtXXFormsTarget;
    private String resolvedXXFormsTarget;
    private String avtXXFormsHandleXInclude;

    private boolean xxfShowProgress;

    private boolean fURLNorewrite;
    private String urlType;

    // All the submission types in the order they must be checked
    private final Submission[] submissions;

    public XFormsModelSubmission(XBLContainer container, org.orbeon.oxf.xforms.analysis.model.Submission staticSubmission, XFormsModel model) {
        this.staticSubmission = staticSubmission;

        this.id = staticSubmission.staticId();
        this.submissionElement = staticSubmission.element();

        this.container = container;
        this.containingDocument = container.getContainingDocument();

        this.model = model;

        this.submissions = new Submission[] {
            new EchoSubmission(this),
            new ClientGetAllSubmission(this),
            new FilterPortletSubmission(this),
            new CacheableSubmission(this),
            new LocalPortletSubmission(this),
            new RequestDispatcherSubmission(this),
            new RegularSubmission(this)
        };
    }

    public XFormsContainingDocument containingDocument() {
        return containingDocument;
    }

    public Element getSubmissionElement() {
        return submissionElement;
    }


    public boolean isShowProgress() {
        return xxfShowProgress;
    }

    public boolean isURLNorewrite() {
        return fURLNorewrite;
    }

    public String getUrlType() {
        return urlType;
    }

    public String getReplace() {
        return replace;
    }

    public String getTargetref() {
        return targetref;
    }

    public String getResolvedXXFormsTarget() {
        return resolvedXXFormsTarget;
    }

    private void extractSubmissionElement() {
        if (!submissionElementExtracted) {

            avtActionOrResource = submissionElement.attributeValue(XFormsConstants.RESOURCE_QNAME);
            if (avtActionOrResource == null) // @resource has precedence over @action
                avtActionOrResource = submissionElement.attributeValue("action");
            if (avtActionOrResource == null) {
                // TODO: support XForms 1.1 nested xf:resource element
                throw new XFormsSubmissionException(this, "xf:submission: action attribute or resource attribute is missing.",
                        "processing xf:submission attributes");
            }

            avtMethod = submissionElement.attributeValue("method");
            if (avtMethod == null) {
                // TODO: support XForms 1.1 nested xf:method element
                throw new XFormsSubmissionException(this, "xf:submission: method attribute is missing.",
                        "processing xf:submission attributes");
            }
            avtValidate = submissionElement.attributeValue("validate");
            avtRelevant = submissionElement.attributeValue("relevant");
            avtXXFormsCalculate = submissionElement.attributeValue(XFormsConstants.XXFORMS_CALCULATE_QNAME);
            avtXXFormsUploads = submissionElement.attributeValue(XFormsConstants.XXFORMS_UPLOADS_QNAME);
            avtXXFormsAnnotate = submissionElement.attributeValue(XFormsConstants.XXFORMS_ANNOTATE_QNAME);

            avtSerialization = submissionElement.attributeValue("serialization");

            // @targetref is the new name as of May 2009, and @target is still supported for backward compatibility
            targetref = submissionElement.attributeValue("targetref");
            if (targetref == null)
                targetref = submissionElement.attributeValue(XFormsConstants.TARGET_QNAME);

            avtMode = submissionElement.attributeValue("mode");

            avtVersion = submissionElement.attributeValue("version");

            avtIndent = submissionElement.attributeValue("indent");
            avtMediatype = submissionElement.attributeValue("mediatype");
            avtEncoding = submissionElement.attributeValue("encoding");
            avtOmitxmldeclaration = submissionElement.attributeValue("omit-xml-declaration");
            avtStandalone = submissionElement.attributeValue("standalone");

            // TODO
//            cdatasectionelements = submissionElement.attributeValue("cdata-section-elements");
            if (submissionElement.attributeValue("replace") != null) {
                replace = submissionElement.attributeValue("replace");

                if (replace.equals("instance")) {
                    replaceInstanceId = submissionElement.attributeValue("instance");
                    xxfReplaceInstanceId = submissionElement.attributeValue(XFormsConstants.XXFORMS_INSTANCE_QNAME);
                }
            }
            if (submissionElement.attributeValue("separator") != null) {
                avtSeparator = submissionElement.attributeValue("separator");
            }
            // TODO
//            includenamespaceprefixes = submissionElement.attributeValue("includenamespaceprefixes");

            // Extension attributes
            avtXXFormsUsername = submissionElement.attributeValue(XFormsConstants.XXFORMS_USERNAME_QNAME);
            avtXXFormsPassword = submissionElement.attributeValue(XFormsConstants.XXFORMS_PASSWORD_QNAME);
            avtXXFormsPreemptiveAuthentication = submissionElement.attributeValue(XFormsConstants.XXFORMS_PREEMPTIVE_AUTHENTICATION_QNAME);
            avtXXFormsDomain = submissionElement.attributeValue(XFormsConstants.XXFORMS_DOMAIN_QNAME);

            avtXXFormsReadonly = submissionElement.attributeValue(XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_QNAME);
            avtXXFormsShared = submissionElement.attributeValue(XFormsConstants.XXFORMS_SHARED_QNAME);
            avtXXFormsCache = submissionElement.attributeValue(XFormsConstants.XXFORMS_CACHE_QNAME);

            avtXXFormsTarget = submissionElement.attributeValue(XFormsConstants.XXFORMS_TARGET_QNAME);
            avtXXFormsHandleXInclude = submissionElement.attributeValue(XFormsConstants.XXFORMS_XINCLUDE);

            // Whether we must show progress or not
            xxfShowProgress = !"false".equals(submissionElement.attributeValue(XFormsConstants.XXFORMS_SHOW_PROGRESS_QNAME));

            // Whether or not to rewrite URLs
            fURLNorewrite = XFormsUtils.resolveUrlNorewrite(submissionElement);

            // URL type
            urlType = submissionElement.attributeValue(XMLConstants.FORMATTING_URL_TYPE_QNAME);

            // Remember that we did this
            submissionElementExtracted = true;
        }
    }

    public String getId() {
        return id;
    }

    public String getPrefixedId() {
        return XFormsUtils.getPrefixedId(getEffectiveId());
    }

    public Scope scope() {
        return staticSubmission.scope();
    }

    public String getEffectiveId() {
        return XFormsUtils.getRelatedEffectiveId(model.getEffectiveId(), getId());
    }

    public XBLContainer container() {
        return getModel().container();
    }

    public LocationData getLocationData() {
        return (LocationData) submissionElement.getData();
    }

    public XFormsEventObserver parentEventObserver() {
        return model;
    }

    public XFormsModel getModel() {
        return model;
    }

    public void performDefaultAction(XFormsEvent event) {
        final String eventName = event.name();

        if (XFormsEvents.XFORMS_SUBMIT.equals(eventName) || XFormsEvents.XXFORMS_SUBMIT.equals(eventName)) {
            // 11.1 The xforms-submit Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None
            doSubmit(event);
        } else if (XFormsEvents.XXFORMS_ACTION_ERROR.equals(eventName)) {
            final XXFormsActionErrorEvent ev = (XXFormsActionErrorEvent) event;
            XFormsError.handleNonFatalActionError(this, ev.throwable());
        }
    }

    private void doSubmit(XFormsEvent event) {
        containingDocument.setGotSubmission();

        final IndentedLogger indentedLogger = getIndentedLogger();

        // Variables declared here as they are used in a catch/finally block
        SubmissionParameters p = null;
        String resolvedActionOrResource = null;

        // Make sure submission element info is extracted
        extractSubmissionElement();

        Runnable submitDoneOrErrorRunnable = null;
        try {
            try {
                // Big bag of initial runtime parameters
                p = new SubmissionParameters(event.name());

                if (indentedLogger.isDebugEnabled()) {
                    final String message = p.isDeferredSubmissionFirstPass ? "submission first pass" : p.isDeferredSubmissionSecondPass ? "submission second pass" : "submission";
                    indentedLogger.startHandleOperation("", message, "id", getEffectiveId());
                }

                // If a submission requiring a second pass was already set, then we ignore a subsequent submission but
                // issue a warning
                {
                    final XFormsModelSubmission existingSubmission = containingDocument.getClientActiveSubmissionFirstPass();
                    if (p.isDeferredSubmission && existingSubmission != null) {
                        indentedLogger.logWarning("", "another submission requiring a second pass already exists",
                                "existing submission", existingSubmission.getEffectiveId(),
                                "new submission", this.getEffectiveId());
                        return;
                    }
                }

                /* ***** Check for pending uploads ********************************************************************** */

                // We can do this first, because the check just depends on the controls, instance to submit, and pending
                // submissions if any. This does not depend on the actual state of the instance.
                if (p.serialize && p.resolvedXXFormsUploads && XFormsSubmissionUtils.hasBoundRelevantPendingUploadControls(containingDocument, p.refInstance)) {
                    throw new XFormsSubmissionException(this, "xf:submission: instance to submit has at least one pending upload.",
                        "checking pending uploads",
                        new XFormsSubmitErrorEvent(XFormsModelSubmission.this, XFormsSubmitErrorEvent.XXFORMS_PENDING_UPLOADS(), null));
                }

                /* ***** Update data model ****************************************************************************** */

                // "The data model is updated"
                final XFormsModel modelForInstance;
                if (p.refInstance != null) {
                    modelForInstance = p.refInstance.model();
                    if (modelForInstance != null) {
                        // NOTE: XForms 1.1 says that we should rebuild/recalculate the "model containing this submission".
                        // Here, we rebuild/recalculate instead the model containing the submission's single-node binding.
                        // This can be different than the model containing the submission if using e.g. xxf:instance().

                        // NOTE: XForms 1.1 seems to say this should happen regardless of whether we serialize or not. If
                        // the instance is not serialized and if no instance data is otherwise used for the submission,
                        // this seems however unneeded so we optimize out.
                        if (p.resolvedValidate || p.resolvedRelevant || p.resolvedXXFormsCalculate) {
                            // Rebuild impacts validation, relevance and calculated values (set by recalculate)
                            modelForInstance.doRebuild();
                        }
                        if (p.resolvedRelevant || p.resolvedXXFormsCalculate) {
                            // Recalculate impacts relevance and calculated values
                            modelForInstance.doRecalculateRevalidate(false);
                        }
                    }
                } else {
                    // Case where no instance was found
                    modelForInstance = null;
                }

                /* ***** Handle deferred submission ********************************************************************* */

                // Resolve the target AVT because XFormsServer requires it for deferred submission
                resolvedXXFormsTarget = XFormsUtils.resolveAttributeValueTemplates(containingDocument, p.xpathContext, p.refNodeInfo, avtXXFormsTarget);

                // Deferred submission: end of the first pass
                if (p.isDeferredSubmissionFirstPass) {

                    // Create (but abandon) document to submit here because in case of error, an Ajax response will still be produced
                    if (p.serialize) {
                        createDocumentToSubmit(indentedLogger, p.refNodeInfo, p.refInstance, modelForInstance, p.resolvedValidate, p.resolvedRelevant, p.resolvedXXFormsAnnotate);
                    }

                    // When replace="all", we wait for the submission of an XXFormsSubmissionEvent from the client
                    containingDocument.setActiveSubmissionFirstPass(this);
                    return;
                }

                /* ***** Submission second pass ************************************************************************* */

                // Compute parameters only needed during second pass
                final SecondPassParameters p2 = new SecondPassParameters(p);
                resolvedActionOrResource = p2.actionOrResource; // in case of exception

                /* ***** Serialization ********************************************************************************** */

                // Get serialization requested from @method and @serialization attributes
                final String requestedSerialization = XFormsSubmissionUtils.getRequestedSerialization(p.serialization, p.resolvedMethod);
                if (requestedSerialization == null)
                    throw new XFormsSubmissionException(this, "xf:submission: invalid submission method requested: " + p.resolvedMethod, "serializing instance");

                final Document documentToSubmit;
                if (p.serialize) {

                    // Check if a submission requires file upload information
                    if (requestedSerialization.startsWith("multipart/")) {
                        // Annotate before re-rooting/pruning
                        XFormsSubmissionUtils.annotateBoundRelevantUploadControls(containingDocument, p.refInstance);
                    }

                    // Create document to submit
                    documentToSubmit = createDocumentToSubmit(indentedLogger, p.refNodeInfo, p.refInstance, modelForInstance, p.resolvedValidate, p.resolvedRelevant, p.resolvedXXFormsAnnotate);

                } else {
                    // Don't recreate document
                    documentToSubmit = null;
                }

                final String overriddenSerializedData;
                if (!p.isDeferredSubmissionSecondPass) {
                    if (p.serialize) {
                        // Fire xforms-submit-serialize

                        // "The event xforms-submit-serialize is dispatched. If the submission-body property of the event
                        // is changed from the initial value of empty string, then the content of the submission-body
                        // property string is used as the submission serialization. Otherwise, the submission serialization
                        // consists of a serialization of the selected instance data according to the rules stated at 11.9
                        // Submission Options."

                        final XFormsSubmitSerializeEvent serializeEvent = new XFormsSubmitSerializeEvent(XFormsModelSubmission.this, p.refNodeInfo, requestedSerialization);
                        Dispatch.dispatchEvent(serializeEvent);

                        // TODO: rest of submission should happen upon default action of event

                        overriddenSerializedData = serializeEvent.submissionBodyAsString();
                    } else {
                        overriddenSerializedData = null;
                    }
                } else {
                    // Two reasons: 1. We don't want to modify the document state 2. This can be called outside of the document
                    // lock, see XFormsServer.
                    overriddenSerializedData = null;
                }

                // Serialize
                final SerializationParameters sp = new SerializationParameters(p, p2, requestedSerialization, documentToSubmit, overriddenSerializedData);

                /* ***** Submission connection ************************************************************************** */

                // Result information
                SubmissionResult submissionResult = null;

                // Iterate through submissions and run the first match
                for (final Submission submission : submissions) {
                    if (submission.isMatch(p, p2, sp)) {
                        if (indentedLogger.isDebugEnabled())
                            indentedLogger.startHandleOperation("", "connecting", "type", submission.getType());
                        try {
                            submissionResult = submission.connect(p, p2, sp);
                            break;
                        } finally {
                            if (indentedLogger.isDebugEnabled())
                                indentedLogger.endHandleOperation();
                        }
                    }
                }

                /* ***** Submission result processing ******************************************************************* */

                // NOTE: handleSubmissionResult() catches Throwable and returns a Runnable
                if (submissionResult != null)// submissionResult is null in case the submission is running asynchronously, AND when ???
                    submitDoneOrErrorRunnable = handleSubmissionResult(p, p2, submissionResult, true); // true because function context might have changed

            } catch (final Throwable throwable) {
                /* ***** Handle errors ********************************************************************************** */
                final SubmissionParameters pVal = p;
                final String resolvedActionOrResourceVal = resolvedActionOrResource;
                submitDoneOrErrorRunnable = new Runnable() {
                    public void run() {
                        if (pVal != null && pVal.isDeferredSubmissionSecondPass && containingDocument.isLocalSubmissionForward()) {
                            // It doesn't serve any purpose here to dispatch an event, so we just propagate the exception
                            throw new XFormsSubmissionException(XFormsModelSubmission.this, throwable, "Error while processing xf:submission", "processing submission");
                        } else {
                            // Any exception will cause an error event to be dispatched
                            sendSubmitError(resolvedActionOrResourceVal, throwable);
                        }
                    }
                };
            }
        } finally {
            // Log total time spent in submission
            if (p != null && indentedLogger.isDebugEnabled()) {
                indentedLogger.endHandleOperation();
            }
        }

        // Execute post-submission code if any
        // This typically dispatches xforms-submit-done/xforms-submit-error, or may throw another exception
        if (submitDoneOrErrorRunnable != null) {
            // We do this outside the above catch block so that if a problem occurs during dispatching xforms-submit-done
            // or xforms-submit-error we don't dispatch xforms-submit-error (which would be illegal).
            // This will also close the connection result if needed.
            submitDoneOrErrorRunnable.run();
        }
    }

    /*
     * Process the response of an asynchronous submission.
     */
    public void doSubmitReplace(SubmissionResult submissionResult) {

        assert submissionResult != null;

        // Big bag of initial runtime parameters
        final SubmissionParameters p = new SubmissionParameters(null);
        final SecondPassParameters p2 = new SecondPassParameters(p);

        final Runnable submitDoneRunnable = handleSubmissionResult(p, p2, submissionResult, false);

        // Execute submit done runnable if any
        if (submitDoneRunnable != null) {
            // Do this outside the handleSubmissionResult catch block so that if a problem occurs during dispatching
            // xforms-submit-done we don't dispatch xforms-submit-error (which would be illegal)
            submitDoneRunnable.run();
        }
    }

    private Runnable handleSubmissionResult(SubmissionParameters p, SecondPassParameters p2, final SubmissionResult submissionResult, boolean initializeXPathContext) {

        assert p != null;
        assert p2 != null;
        assert submissionResult != null;

        Runnable submitDoneOrErrorRunnable = null;
        try {
            final IndentedLogger indentedLogger = getIndentedLogger();
            if (indentedLogger.isDebugEnabled())
                indentedLogger.startHandleOperation("", "handling result");
            try {
                // Get fresh XPath context if requested
                if (initializeXPathContext)
                    p.initializeXPathContext();
                // Process the different types of response
                if (submissionResult.getThrowable() != null) {
                    // Propagate throwable, which might have come from a separate thread
                    submitDoneOrErrorRunnable = new Runnable() {
                        public void run() { sendSubmitError(submissionResult.getThrowable(), submissionResult); }
                    };
                } else {
                    // Replacer provided, perform replacement
                    assert submissionResult.getReplacer() != null;
                    submitDoneOrErrorRunnable = submissionResult.getReplacer().replace(submissionResult.getConnectionResult(), p, p2);
                }
            } finally {
                if (indentedLogger.isDebugEnabled())
                    indentedLogger.endHandleOperation();
            }
        } catch (final Throwable throwable) {
            // Any exception will cause an error event to be dispatched
            submitDoneOrErrorRunnable = new Runnable() {
                public void run() { sendSubmitError(throwable, submissionResult); }
            };
        }

        // Create wrapping runnable to make sure the submission result is closed
        final Runnable finalSubmitDoneOrErrorRunnable = submitDoneOrErrorRunnable;
        return new Runnable() {
            public void run() {
                try {
                    if (finalSubmitDoneOrErrorRunnable != null)
                        finalSubmitDoneOrErrorRunnable.run();
                } finally {
                    // Close only after the submission result has run
                    submissionResult.close();
                }
            }
        };
    }

    /**
     * Run the given submission callable. This must be a callable for a replace="all" submission.
     *
     * @param callable          callable run
     * @param response          response to write to if needed
     */
    public static void runDeferredSubmission(Callable<SubmissionResult> callable, ExternalContext.Response response) {
        // Run submission
        try {
            final SubmissionResult result = callable.call();
            if (result != null) {
                // Callable did not do all the work, completed it here
                try {
                    if (result.getReplacer() != null) {
                        // Replacer provided, perform replacement
                        if (result.getReplacer() instanceof AllReplacer)
                            AllReplacer.replace(result.getConnectionResult(), response);
                        else if (result.getReplacer() instanceof RedirectReplacer)
                            RedirectReplacer.replace(result.getConnectionResult(), response);
                        else
                            assert result.getReplacer() instanceof NoneReplacer;
                    } else if (result.getThrowable() != null) {
                        // Propagate throwable, which might have come from a separate thread
                        throw new OXFException(result.getThrowable());
                    } else {
                        // Should not happen
                    }
                } finally {
                    result.close();
                }
            }
        } catch (Exception e) {
            // Something bad happened
            throw new OXFException(e);
        }
    }

    public Runnable sendSubmitDone(final ConnectionResult connectionResult) {
        return new Runnable() {
            public void run() {
                // After a submission, the context might have changed
                model.resetAndEvaluateVariables();

                Dispatch.dispatchEvent(new XFormsSubmitDoneEvent(XFormsModelSubmission.this, connectionResult));
            }
        };
    }

    private void sendSubmitError(Throwable throwable, SubmissionResult submissionResult) {

        // After a submission, the context might have changed
        model.resetAndEvaluateVariables();

        // Try to get error event from exception
        XFormsSubmitErrorEvent submitErrorEvent = null;
        if (throwable instanceof XFormsSubmissionException) {
            final XFormsSubmissionException submissionException = (XFormsSubmissionException) throwable;
            submitErrorEvent = submissionException.getXFormsSubmitErrorEvent();
        }

        // If no event obtained, create default event
        if (submitErrorEvent == null) {
            submitErrorEvent = new XFormsSubmitErrorEvent(XFormsModelSubmission.this,
                submitErrorEvent.XXFORMS_INTERNAL_ERROR(), submissionResult.getConnectionResult());
        }

        // Dispatch event
        submitErrorEvent.logThrowable(throwable);
        Dispatch.dispatchEvent(submitErrorEvent);
    }

    private void sendSubmitError(String resolvedActionOrResource, Throwable throwable) {

        // After a submission, the context might have changed
        model.resetAndEvaluateVariables();

        // Try to get error event from exception
        XFormsSubmitErrorEvent submitErrorEvent = null;
        if (throwable instanceof XFormsSubmissionException) {
            final XFormsSubmissionException submissionException = (XFormsSubmissionException) throwable;
            submitErrorEvent = submissionException.getXFormsSubmitErrorEvent();
        }

        // If no event obtained, create default event
        if (submitErrorEvent == null) {
            submitErrorEvent = new XFormsSubmitErrorEvent(XFormsModelSubmission.this, scala.Option.apply(resolvedActionOrResource),
                submitErrorEvent.XXFORMS_INTERNAL_ERROR(), 0);
        }

        // Dispatch event
        submitErrorEvent.logThrowable(throwable);
        Dispatch.dispatchEvent(submitErrorEvent);
    }

    public Replacer getReplacer(ConnectionResult connectionResult, SubmissionParameters p) throws IOException {

        // NOTE: This can be called from other threads so it must NOT modify the XFCD or submission

        if (connectionResult != null) {
            // Handle response
            final Replacer replacer;
            if (connectionResult.dontHandleResponse()) {
                // Always return a replacer even if it does nothing, this way we don't have to deal with null
                replacer = new NoneReplacer(this, containingDocument);
            } else if (NetUtils.isSuccessCode(connectionResult.statusCode())) {
                // Successful response
                if (connectionResult.hasContent()) {
                    // There is a body

                    // Get replacer
                    if (p.isReplaceAll) {
                        replacer = new AllReplacer(this, containingDocument);
                    } else if (p.isReplaceInstance) {
                        replacer = new InstanceReplacer(this, containingDocument);
                    } else if (p.isReplaceText) {
                        replacer = new TextReplacer(this, containingDocument);
                    } else if (p.isReplaceNone) {
                        replacer = new NoneReplacer(this, containingDocument);
                    } else {
                        throw new XFormsSubmissionException(this, "xf:submission: invalid replace attribute: " + replace, "processing instance replacement",
                                new XFormsSubmitErrorEvent(this, XFormsSubmitErrorEvent.XXFORMS_INTERNAL_ERROR(), connectionResult));
                    }
                } else {
                    // There is no body, notify that processing is terminated
                    if (p.isReplaceInstance || p.isReplaceText) {
                        // XForms 1.1 says it is fine not to have a body, but in most cases you will want to know that
                        // no instance replacement took place
                        final IndentedLogger indentedLogger = getIndentedLogger();
                        indentedLogger.logWarning("", "instance or text replacement did not take place upon successful response because no body was provided.",
                                "submission id", getEffectiveId());
                    }

                    // "For a success response not including a body, submission processing concludes after dispatching
                    // xforms-submit-done"
                    replacer = new NoneReplacer(this, containingDocument);
                }
            } else if (NetUtils.isRedirectCode(connectionResult.statusCode())) {
                // Got a redirect

                // Currently we don't know how to handle a redirect for replace != "all"
                if (!p.isReplaceAll)
                    throw new XFormsSubmissionException(this, "xf:submission for submission id: " + id + ", redirect code received with replace=\"" + replace + "\"", "processing submission response",
                            new XFormsSubmitErrorEvent(this, XFormsSubmitErrorEvent.RESOURCE_ERROR(), connectionResult));

                replacer = new RedirectReplacer(this, containingDocument);

            } else {
                // Error code received
                throw new XFormsSubmissionException(this, "xf:submission for submission id: " + id + ", error code received when submitting instance: " + connectionResult.statusCode(), "processing submission response",
                        new XFormsSubmitErrorEvent(this, XFormsSubmitErrorEvent.RESOURCE_ERROR(), connectionResult));
            }

            return replacer;
        } else {
            return null;
        }
    }

    public class SubmissionParameters {

        // @replace attribute
        final boolean isReplaceAll = replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL);
        final boolean isReplaceInstance = replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_INSTANCE);
        final boolean isReplaceText = replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_TEXT);
        final boolean isReplaceNone = replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_NONE);

        // Current node for xf:submission and instance containing the node to submit
        NodeInfo refNodeInfo;
        XFormsInstance refInstance;
        Item submissionElementContextItem;

        final String resolvedMethod;
        final String actualHttpMethod;
        final String resolvedMediatype;
        final String serialization;
        final boolean serialize;

        final boolean resolvedValidate;
        final boolean resolvedRelevant;
        final boolean resolvedXXFormsCalculate;
        final boolean resolvedXXFormsUploads;
        final String resolvedXXFormsAnnotate;

        final boolean isHandlingClientGetAll;

        // XPath function library and namespace mappings
        final FunctionLibrary functionLibrary = XFormsContainingDocument.getFunctionLibrary();
        final NamespaceMapping namespaceMapping = container.getNamespaceMappings(submissionElement);

        // XPath context
        XPathCache.XPathContext xpathContext;

        final boolean isNoscript;
        final boolean isAllowDeferredSubmission;

        final boolean isPossibleDeferredSubmission;
        final boolean isDeferredSubmission;
        final boolean isDeferredSubmissionFirstPass;
        final boolean isDeferredSubmissionSecondPass;

        public void initializeXPathContext() {

            final BindingContext bindingContext; {
                model.resetAndEvaluateVariables();
                final XFormsContextStack contextStack = model.getContextStack();
                contextStack.pushBinding(getSubmissionElement(), getEffectiveId(), model.getResolutionScope());
                bindingContext = contextStack.getCurrentBindingContext();
            }

            final XFormsFunction.Context functionContext = model.getContextStack().getFunctionContext(getEffectiveId());

            refNodeInfo = (NodeInfo) bindingContext.getSingleItem();
            submissionElementContextItem = bindingContext.contextItem();
            // NOTE: Current instance may be null if the document submitted is not part of an instance
            refInstance = bindingContext.instanceOrNull();
            xpathContext = new XPathCache.XPathContext(namespaceMapping, bindingContext.getInScopeVariables(), functionLibrary, functionContext, null, getLocationData());
        }

        public SubmissionParameters(String eventName) {

            initializeXPathContext();

            // Check that we have a current node and that it is pointing to a document or an element
            if (refNodeInfo == null)
                throw new XFormsSubmissionException(XFormsModelSubmission.this, "Empty single-node binding on xf:submission for submission id: " + id, "getting submission single-node binding",
                        new XFormsSubmitErrorEvent(XFormsModelSubmission.this, XFormsSubmitErrorEvent.NO_DATA(), null));

            if (!(refNodeInfo instanceof DocumentInfo || refNodeInfo.getNodeKind() == org.w3c.dom.Node.ELEMENT_NODE)) {
                throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: single-node binding must refer to a document node or an element.", "getting submission single-node binding",
                        new XFormsSubmitErrorEvent(XFormsModelSubmission.this, XFormsSubmitErrorEvent.NO_DATA(), null));
            }

            {
                // Resolved method AVT
                final String resolvedMethodQName = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo , avtMethod);
                resolvedMethod = Dom4jUtils.qNameToExplodedQName(Dom4jUtils.extractTextValueQName(namespaceMapping.mapping, resolvedMethodQName, true));

                // Get actual method based on the method attribute
                actualHttpMethod = XFormsSubmissionUtils.getActualHttpMethod(resolvedMethod);

                // Get mediatype
                resolvedMediatype = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo, avtMediatype);

                // Serialization
                serialization = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo, avtSerialization);
                if (serialization != null) {
                    serialize = !serialization.equals("none");
                } else {
                    // For backward compatibility only, support @serialize if there is no @serialization attribute (was in early XForms 1.1 draft)
                    serialize = !"false".equals(submissionElement.attributeValue("serialize"));
                }

                // Resolve validate and relevant AVTs
                final String resolvedValidateString = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo , avtValidate);
                // "The default value is "false" if the value of serialization is "none" and "true" otherwise"
                resolvedValidate = serialize && !"false".equals(resolvedValidateString);

                final String resolvedRelevantString = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo , avtRelevant);
                // "The default value is "false" if the value of serialization is "none" and "true" otherwise"
                resolvedRelevant = serialize && !"false".equals(resolvedRelevantString);

                final String resolvedCalculateString = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo , avtXXFormsCalculate);
                resolvedXXFormsCalculate = serialize && !"false".equals(resolvedCalculateString);

                final String resolvedUploadsString = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo , avtXXFormsUploads);
                resolvedXXFormsUploads = serialize && !"false".equals(resolvedUploadsString);

                final String resolvedAnnotateString = XFormsUtils.resolveAttributeValueTemplates(containingDocument, xpathContext, refNodeInfo , avtXXFormsAnnotate);
                resolvedXXFormsAnnotate = serialize ? resolvedAnnotateString : null;
            }

            isHandlingClientGetAll = containingDocument.isOptimizeGetAllSubmission() && actualHttpMethod.equals("GET")
                    && isReplaceAll
                    && (resolvedMediatype == null || !resolvedMediatype.startsWith(NetUtils.APPLICATION_SOAP_XML)) // can't let SOAP requests be handled by the browser
                    && avtXXFormsUsername == null // can't optimize if there are authentication credentials
                    && avtXXFormsTarget == null   // can't optimize if there is a target
                    && Dom4jUtils.elements(getSubmissionElement(), XFormsConstants.XFORMS_HEADER_QNAME).size() == 0; // can't optimize if there are headers specified

            // In noscript mode, or in "Ajax portlet" mode, there is no deferred submission process
            // Also don't allow deferred submissions when the incoming method is a GET. This is an indirect way of
            // allowing things like using the XForms engine to generate a PDF with an HTTP GET.

            // NOTE: Method can be null e.g. in a portlet render request
            final String method = NetUtils.getExternalContext().getRequest().getMethod();

            isNoscript = containingDocument.noscript();
            isAllowDeferredSubmission = !isNoscript && !(method != null && method.equals("GET"));

            isPossibleDeferredSubmission = isReplaceAll && !isHandlingClientGetAll && !containingDocument.isInitializing();
            isDeferredSubmission = isAllowDeferredSubmission && isPossibleDeferredSubmission;
            isDeferredSubmissionFirstPass = isDeferredSubmission && XFormsEvents.XFORMS_SUBMIT.equals(eventName);
            isDeferredSubmissionSecondPass = isDeferredSubmission && !isDeferredSubmissionFirstPass; // here we get XXFORMS_SUBMIT
        }
    }

    public class SecondPassParameters {

        // This mostly consists of AVTs that can be evaluated only during the second pass of the submission

        final String actionOrResource;
        final String mode;
        final String version;
        final String encoding;
        final String separator;
        final boolean indent;
        final boolean omitxmldeclaration;
        final Boolean standalone;
        final Connection.Credentials credentials;
        final boolean isReadonly;
        final boolean isCache;
        final long timeToLive;
        final boolean isHandleXInclude;

        final boolean isAsynchronous;

        public SecondPassParameters(SubmissionParameters p) {
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtActionOrResource);
                if (temp == null) {
                    // This can be null if, e.g. you have an AVT like resource="{()}"
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: mandatory resource or action evaluated to an empty sequence for attribute value: " + avtActionOrResource,
                            "resolving resource URI");
                }
                actionOrResource = NetUtils.encodeHRRI(temp, true);
                // TODO: see if we can resolve xml:base early to detect absolute URLs early as well
//                actionOrResource = XFormsUtils.resolveXMLBase(containingDocument, getSubmissionElement(), NetUtils.encodeHRRI(temp, true)).toString();
            }

            mode = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtMode);
            version = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtVersion);
            separator = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtSeparator);

            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtEncoding);
                encoding = (temp != null) ? temp : "UTF-8";
            }
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtIndent);
                indent = Boolean.valueOf(temp);
            }
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtOmitxmldeclaration);
                omitxmldeclaration = Boolean.valueOf(temp);
            }
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtStandalone);
                standalone = (temp != null) ? Boolean.valueOf(temp) : null;
            }

            final String username = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtXXFormsUsername);
            final String password = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtXXFormsPassword);
            final String preemptiveAuthentication = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtXXFormsPreemptiveAuthentication);
            final String domain = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtXXFormsDomain);

            if (StringUtils.isEmpty(username))
                credentials = null;
            else
                credentials = new Connection.Credentials(username, password, preemptiveAuthentication, domain);

            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtXXFormsReadonly);
                isReadonly = (temp != null) ? Boolean.valueOf(temp) : false;
            }

            if (avtXXFormsCache != null) {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtXXFormsCache);
                // New attribute
                isCache = Boolean.valueOf(temp);
            } else {
                // For backward compatibility
                isCache = "application".equals(XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtXXFormsShared));
            }

            timeToLive = Instance.timeToLiveOrDefault(getSubmissionElement());

            // Default is "false" for security reasons
            final String tempHandleXInclude = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext, p.refNodeInfo, avtXXFormsHandleXInclude);
            isHandleXInclude = Boolean.valueOf(tempHandleXInclude);

            // Check read-only and cache hints
            if (isCache) {
                if (!(p.actualHttpMethod.equals("GET") || p.actualHttpMethod.equals("POST") || p.actualHttpMethod.equals("PUT")))
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: xxf:cache=\"true\" or xxf:shared=\"application\" can be set only with method=\"get|post|put\".",
                            "checking read-only and shared hints");
                if (!p.isReplaceInstance)
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: xxf:cache=\"true\" or xxf:shared=\"application\" can be set only with replace=\"instance\".",
                            "checking read-only and shared hints");
            } else if (isReadonly) {
                if (!p.isReplaceInstance)
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: xxf:readonly=\"true\" can be \"true\" only with replace=\"instance\".",
                            "checking read-only and shared hints");
            }

            // Get async/sync
            // NOTE: XForms 1.1 default to async, but we don't fully support async so we default to sync instead
            final boolean isRequestedAsynchronousMode = "asynchronous".equals(mode);
            isAsynchronous = !p.isReplaceAll && isRequestedAsynchronousMode;
            if (isRequestedAsynchronousMode && p.isReplaceAll) {
                // For now we don't support replace="all"
                throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: mode=\"asynchronous\" cannot be \"true\" with replace=\"all\".", "checking asynchronous mode");
            }
        }

        protected SecondPassParameters(SecondPassParameters other, boolean isAsynchronous, boolean isReadonly) {
            this.actionOrResource = other.actionOrResource;
            this.version = other.version;
            this.encoding = other.encoding;
            this.separator = other.separator;
            this.indent = other.indent;
            this.omitxmldeclaration = other.omitxmldeclaration;
            this.standalone = other.standalone;
            this.credentials = other.credentials;
            this.isCache = other.isCache;
            this.timeToLive = other.timeToLive;
            this.isHandleXInclude = other.isHandleXInclude;

            this.mode = isAsynchronous ? "asynchronous" : "synchronous";
            this.isAsynchronous = isAsynchronous;
            this.isReadonly = isReadonly;
        }

        public SecondPassParameters amend(boolean isAsynchronous, boolean isReadonly){
            return new SecondPassParameters(this, isAsynchronous, isReadonly);
        }
    }

    public class SerializationParameters {
        final byte[] messageBody;// TODO: provide option for body to be a stream
        final String queryString;
        final String actualRequestMediatype;

        public SerializationParameters(SubmissionParameters p, SecondPassParameters p2, String requestedSerialization, Document documentToSubmit, String overriddenSerializedData) throws Exception {
            if (p.serialize) {
                final String defaultMediatypeForSerialization;
                if (overriddenSerializedData != null && !overriddenSerializedData.equals("")) {
                    // Form author set data to serialize
                    if (Connection.requiresRequestBody(p.actualHttpMethod)) {
                        queryString = null;
                        messageBody = overriddenSerializedData.getBytes("UTF-8");
                        defaultMediatypeForSerialization = "application/xml";
                    } else {
                        queryString = URLEncoder.encode(overriddenSerializedData, "UTF-8");
                        messageBody = null;
                        defaultMediatypeForSerialization = null;
                    }
                } else if (requestedSerialization.equals("application/x-www-form-urlencoded")) {
                    // Perform "application/x-www-form-urlencoded" serialization
                    if (Connection.requiresRequestBody(p.actualHttpMethod)) {
                        queryString = null;
                        messageBody = XFormsSubmissionUtils.createWwwFormUrlEncoded(documentToSubmit, p2.separator).getBytes("UTF-8");// the resulting string is already ASCII in fact
                        defaultMediatypeForSerialization = "application/x-www-form-urlencoded";
                    } else {
                        queryString = XFormsSubmissionUtils.createWwwFormUrlEncoded(documentToSubmit, p2.separator);
                        messageBody = null;
                        defaultMediatypeForSerialization = null;
                    }
                } else if (requestedSerialization.equals("application/xml")) {
                    // Serialize XML to a stream of bytes
                    try {
                        final Transformer identity = TransformerUtils.getIdentityTransformer();
                        TransformerUtils.applyOutputProperties(identity,
                                "xml", p2.version, null, null, p2.encoding, p2.omitxmldeclaration, p2.standalone, p2.indent, 4);

                        // TODO: use cdata-section-elements

                        final ByteArrayOutputStream os = new ByteArrayOutputStream();
                        identity.transform(new DocumentSource(documentToSubmit), new StreamResult(os));
                        messageBody = os.toByteArray();
                    } catch (Exception e) {
                        throw new XFormsSubmissionException(XFormsModelSubmission.this, e, "xf:submission: exception while serializing instance to XML.", "serializing instance");
                    }
                    defaultMediatypeForSerialization = "application/xml";
                    queryString = null;
                } else if (requestedSerialization.equals("multipart/related")) {
                    // TODO
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: submission serialization not yet implemented: " + requestedSerialization, "serializing instance");
                } else if (requestedSerialization.equals("multipart/form-data")) {
                    // Build multipart/form-data body

                    // Create and set body
                    final MultipartEntity multipartFormData = XFormsSubmissionUtils.createMultipartFormData(documentToSubmit);

                    final ByteArrayOutputStream os = new ByteArrayOutputStream();
                    multipartFormData.writeTo(os);

                    messageBody = os.toByteArray();
                    queryString = null;

                    // The mediatype also contains the boundary
                    defaultMediatypeForSerialization = multipartFormData.getContentType().getValue();

                } else if (requestedSerialization.equals("application/octet-stream")) {
                    // Binary serialization
                    final QName nodeType = InstanceData.getType(documentToSubmit.getRootElement());

                    if (XMLConstants.XS_BASE64BINARY_QNAME.equals(nodeType)) {
                        // TODO
                        throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: binary serialization with base64Binary type is not yet implemented.", "serializing instance");
                    } else {
                        // Default to anyURI
                        // TODO: PERFORMANCE: Must pass InputStream all the way to the submission instead of storing into byte[] in memory!

                        // NOTE: We support a relative path, in which case the path is resolved as a service URL
                        final String resolvedURI =
                            XFormsUtils.resolveServiceURL(
                                containingDocument,
                                    getSubmissionElement(),
                                    documentToSubmit.getRootElement().getStringValue(),
                                    ExternalContext.Response.REWRITE_MODE_ABSOLUTE);

                        try {
                            messageBody = SubmissionUtils.readByteArray(model, resolvedURI);
                        } catch(Exception e) {
                            throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: binary serialization with anyURI type failed reading URL.", "serializing instance");
                        }
                    }
                    defaultMediatypeForSerialization = "application/octet-stream";
                    queryString = null;
                } else if (requestedSerialization.equals("text/html") || requestedSerialization.equals("application/xhtml+xml")) {
                    // HTML or XHTML serialization
                    try {
                        final Transformer identity = TransformerUtils.getIdentityTransformer();
                        TransformerUtils.applyOutputProperties(identity,
                                requestedSerialization.equals("text/html") ? "html" : "xhtml", p2.version, null, null,
                                p2.encoding, p2.omitxmldeclaration, p2.standalone, p2.indent, 4);

                        // TODO: use cdata-section-elements

                        final ByteArrayOutputStream os = new ByteArrayOutputStream();
                        identity.transform(new DocumentSource(documentToSubmit), new StreamResult(os));
                        messageBody = os.toByteArray();
                    } catch (Exception e) {
                        throw new XFormsSubmissionException(XFormsModelSubmission.this, e, "xf:submission: exception while serializing instance to HTML or XHTML.", "serializing instance");
                    }
                    defaultMediatypeForSerialization = requestedSerialization;
                    queryString = null;
                } else if (XMLUtils.isTextOrJSONContentType(requestedSerialization)) {
                    // Text serialization
                    try {
                        final Transformer identity = TransformerUtils.getIdentityTransformer();
                        TransformerUtils.applyOutputProperties(identity,
                                "text", null, null, null, p2.encoding, true, false, false, 0);

                        final ByteArrayOutputStream os = new ByteArrayOutputStream();
                        identity.transform(new DocumentSource(documentToSubmit), new StreamResult(os));
                        messageBody = os.toByteArray();
                    } catch (Exception e) {
                        throw new XFormsSubmissionException(XFormsModelSubmission.this, e, "xf:submission: exception while serializing instance to text.", "serializing instance");
                    }
                    defaultMediatypeForSerialization = requestedSerialization;
                    queryString = null;
                } else {
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: invalid submission serialization requested: " + requestedSerialization, "serializing instance");
                }

                // Actual request mediatype: the one specified by @mediatype, or the default mediatype for the serialization otherwise
                actualRequestMediatype = (p.resolvedMediatype == null) ? defaultMediatypeForSerialization : p.resolvedMediatype;
            } else {
                queryString = null;
                messageBody = null;
                actualRequestMediatype = null;
            }
        }
    }

    public XFormsInstance findReplaceInstanceNoTargetref(XFormsInstance refInstance) {
        final XFormsInstance replaceInstance;
        if (xxfReplaceInstanceId != null)
            replaceInstance = containingDocument.findInstanceOrNull(xxfReplaceInstanceId);
        else if (replaceInstanceId != null)
            replaceInstance = model.getInstance(replaceInstanceId);
        else if (refInstance == null)
            replaceInstance = model.getDefaultInstance();
        else
            replaceInstance = refInstance;
        return replaceInstance;
    }

    public NodeInfo evaluateTargetRef(XPathCache.XPathContext xpathContext,
                                      XFormsInstance defaultReplaceInstance, Item submissionElementContextItem) {
        final Object destinationObject;
        if (targetref == null) {
            // There is no explicit @targetref, so the target is implicitly the root element of either the instance
            // pointed to by @ref, or the instance specified by @instance or @xxf:instance.
            destinationObject = defaultReplaceInstance.rootElement();
        } else {
            // There is an explicit @targetref, which must be evaluated.

            // "The in-scope evaluation context of the submission element is used to evaluate the expression." BUT ALSO "The
            // evaluation context for this attribute is the in-scope evaluation context for the submission element, except
            // the context node is modified to be the document element of the instance identified by the instance attribute
            // if it is specified."
            final boolean hasInstanceAttribute = xxfReplaceInstanceId != null || replaceInstanceId != null;
            final Item targetRefContextItem = hasInstanceAttribute
                    ? defaultReplaceInstance.rootElement() : submissionElementContextItem;

            // Evaluate destination node
            // "This attribute is evaluated only once a successful submission response has been received and if the replace
            // attribute value is "instance" or "text". The first node rule is applied to the result."
            destinationObject = XPathCache.evaluateSingle(xpathContext, targetRefContextItem, targetref, containingDocument().getRequestStats().getReporter());
        }

        // TODO: Also detect readonly node/ancestor situation
        if (destinationObject instanceof NodeInfo && ((NodeInfo) destinationObject).getNodeKind() == org.w3c.dom.Node.ELEMENT_NODE)
            return (NodeInfo) destinationObject;
        else
            return null;
    }

    public void performTargetAction(XFormsEvent event) {
        // NOP
    }

    private Document createDocumentToSubmit(IndentedLogger indentedLogger, NodeInfo currentNodeInfo,
                                            XFormsInstance currentInstance, XFormsModel modelForInstance,
                                            boolean resolvedValidate, boolean resolvedRelevant, String resolvedAnnotate) {
        final Document documentToSubmit;

        // Revalidate instance
        // NOTE: We need to do this before pruning so that bind/@type works correctly. XForms 1.1 seems to say that this
        // must be done after pruning, but then it is not clear how XML Schema validation would work then.
        // Also, if validate="false" or if serialization="none", then we do not revalidate. Now whether this optimization
        // is acceptable depends on whether validate="false" only means "don't check the instance's validity" or also
        // don't even recalculate. If the latter, then this also means that type annotations won't be updated, which
        // can impact serializations that use type information, for example multipart. But in that case, here we decide
        // the optimization is worth it anyway.
        if (resolvedValidate && modelForInstance != null)
            modelForInstance.doRecalculateRevalidate(false);

        // Get selected nodes (re-root and prune)
        documentToSubmit = reRootAndPrune(currentNodeInfo, resolvedRelevant, resolvedAnnotate);

        // Check that there are no validation errors
        // NOTE: If the instance is read-only, it can't have MIPs at the moment, and can't fail validation/requiredness, so we don't go through the process at all.
        final boolean instanceSatisfiesValidRequired
                = (currentInstance != null && currentInstance.readonly())
                || !resolvedValidate
                || XFormsSubmissionUtils.isSatisfiesValid(indentedLogger, documentToSubmit, true);
        if (!instanceSatisfiesValidRequired) {
            if (indentedLogger.isDebugEnabled()) {
                final String documentString = TransformerUtils.tinyTreeToString(currentNodeInfo);
                indentedLogger.logDebug("", "instance document or subset thereof cannot be submitted",
                        "document", documentString);
            }
            throw new XFormsSubmissionException(this, "xf:submission: instance to submit does not satisfy valid and/or required model item properties.",
                    "checking instance validity",
                    new XFormsSubmitErrorEvent(XFormsModelSubmission.this, XFormsSubmitErrorEvent.VALIDATION_ERROR(), null));
        }

        return documentToSubmit;
    }

    private Document reRootAndPrune(final NodeInfo currentNodeInfo, boolean resolvedRelevant, String resolvedAnnotate) {

        final Document documentToSubmit;
        if (currentNodeInfo instanceof VirtualNode) {
            final Node currentNode = (Node) ((VirtualNode) currentNodeInfo).getUnderlyingNode();

            // "A node from the instance data is selected, based on attributes on the submission
            // element. The indicated node and all nodes for which it is an ancestor are considered for
            // the remainder of the submit process. "
            if (currentNode instanceof Element) {
                // Create subset of document
                documentToSubmit = Dom4jUtils.createDocumentCopyParentNamespaces((Element) currentNode);
            } else {
                // Use entire instance document
                documentToSubmit = Dom4jUtils.createDocumentCopyElement(currentNode.getDocument().getRootElement());
            }

            if (resolvedRelevant) {
                // "Any node which is considered not relevant as defined in 6.1.4 is removed."
                final Node[] nodeToDetach = new Node[1];
                do {
                    // NOTE: This is not very efficient, but at least we avoid NPEs that we would get by
                    // detaching elements within accept(). Should implement a more efficient algorithm to
                    // prune non-relevant nodes.
                    nodeToDetach[0] = null;
                    documentToSubmit.accept(new VisitorSupport() {

                        public final void visit(Element element) {
                            checkInstanceData(element);
                        }

                        public final void visit(Attribute attribute) {
                            checkInstanceData(attribute);
                        }

                        private void checkInstanceData(Node node) {
                            if (nodeToDetach[0] == null) {
                                // Check "relevant" MIP and remove non-relevant nodes
                                if (! InstanceData.getInheritedRelevant(node))
                                    nodeToDetach[0] = node;
                            }
                        }
                    });
                    if (nodeToDetach[0] != null)
                        nodeToDetach[0].detach();

                } while (nodeToDetach[0] != null);
            }

            // Annotate with alerts if needed
            if (StringUtils.isNotBlank(resolvedAnnotate))
                annotateWithAlerts(containingDocument, documentToSubmit, resolvedAnnotate);

            // TODO: handle includenamespaceprefixes
        } else {
            // Submitting read-only instance backed by TinyTree (no MIPs to check)
            if (currentNodeInfo.getNodeKind() == org.w3c.dom.Node.ELEMENT_NODE) {
                documentToSubmit = TransformerUtils.tinyTreeToDom4j(currentNodeInfo);
            } else {
                documentToSubmit = TransformerUtils.tinyTreeToDom4j(currentNodeInfo.getRoot());
            }
        }
        return documentToSubmit;
    }

    public IndentedLogger getIndentedLogger() {
        return containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY);
    }

    public IndentedLogger getDetailsLogger(final XFormsModelSubmission.SubmissionParameters p, final XFormsModelSubmission.SecondPassParameters p2) {
        return getNewLogger(p, p2, getIndentedLogger(), isLogDetails());
    }

    public IndentedLogger getTimingLogger(final XFormsModelSubmission.SubmissionParameters p, final XFormsModelSubmission.SecondPassParameters p2) {
        final IndentedLogger indentedLogger = getIndentedLogger();
        return getNewLogger(p, p2, indentedLogger, indentedLogger.isDebugEnabled());
    }

    private static IndentedLogger getNewLogger(final XFormsModelSubmission.SubmissionParameters p, final XFormsModelSubmission.SecondPassParameters p2,
                                        IndentedLogger indentedLogger, boolean newDebugEnabled) {
        if (p2.isAsynchronous && !p.isReplaceNone) {
            // Background asynchronous submission creates a new logger with its own independent indentation
            final IndentedLogger.Indentation newIndentation = new IndentedLogger.Indentation(indentedLogger.getIndentation().indentation);
            return new IndentedLogger(indentedLogger, newIndentation, newDebugEnabled);
        } else if (indentedLogger.isDebugEnabled() != newDebugEnabled) {
            // Keep shared indentation but use new debug setting
            return new IndentedLogger(indentedLogger, indentedLogger.getIndentation(), newDebugEnabled);
        } else {
            // Synchronous submission or foreground asynchronous submission uses current logger
            return indentedLogger;
        }
    }

    private static boolean isLogDetails() {
        return XFormsProperties.getDebugLogging().contains("submission-details");
    }

    // Only allow xxforms-submit from client
    private static final Set<String> ALLOWED_EXTERNAL_EVENTS = new HashSet<String>();
    static {
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_SUBMIT);
    }

    public boolean allowExternalEvent(String eventName) {
        return ALLOWED_EXTERNAL_EVENTS.contains(eventName);
    }
}
