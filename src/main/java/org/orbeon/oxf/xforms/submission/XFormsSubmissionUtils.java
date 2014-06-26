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

import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.StringBuilderWriter;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * Utilities for XForms submission processing.
 */
public class XFormsSubmissionUtils {
    
    public static String getActualHttpMethod(String methodAttribute) {
        final String actualMethod;
        if (isPost(methodAttribute)) {
            actualMethod = "POST";
        } else if (isGet(methodAttribute)) {
            actualMethod = "GET";
        } else if (isPut(methodAttribute)) {
            actualMethod = "PUT";
        } else if (isDelete(methodAttribute)) {
            actualMethod = "DELETE";
        } else {
            actualMethod = methodAttribute.toUpperCase();
        }
        return actualMethod;
    }

    public static String getRequestedSerialization(String serializationAttribute, String methodAttribute) {
        final String actualSerialization;
        if (serializationAttribute == null) {
            if (methodAttribute.equals("multipart-post")) {
                actualSerialization = "multipart/related";
            } else if (methodAttribute.equals("form-data-post")) {
                actualSerialization = "multipart/form-data";
            } else if (methodAttribute.equals("urlencoded-post")) {
                actualSerialization = "application/x-www-form-urlencoded";
            } else if (XFormsSubmissionUtils.isPost(methodAttribute) || XFormsSubmissionUtils.isPut(methodAttribute)) {
                actualSerialization = "application/xml";
            } else if (XFormsSubmissionUtils.isGet(methodAttribute) || XFormsSubmissionUtils.isDelete(methodAttribute)) {
                actualSerialization = "application/x-www-form-urlencoded";
            } else {
                actualSerialization = null;
            }
        } else {
            actualSerialization = serializationAttribute;
        }
        return actualSerialization;
    }

    public static boolean isGet(String method) {
        return method.equals("get");
    }

    // This is to support XForms's multipart-post, form-data-post and urlencoded-post
    public static boolean isPost(String method) {
        return method.equals("post") || method.endsWith("-post");
    }

    public static boolean isPut(String method) {
        return method.equals("put");
    }

    public static boolean isDelete(String method) {
        return method.equals("delete");
    }

    /**
     * Check whether an XML sub-tree satisfies validity and required MIPs.
     *
     * @param indentedLogger        logger
     * @param startNode             node to check
     * @param recurse               whether to recurse into attributes and descendant nodes
     * @return                      true iif the sub-tree passes the checks
     */
    public static boolean isSatisfiesValid(final IndentedLogger indentedLogger, final Node startNode, boolean recurse) {

        if (recurse) {
            // Recurse into attributes and descendant nodes
            final boolean[] instanceSatisfiesValidRequired = new boolean[]{true};
            startNode.accept(new VisitorSupport() {

                public final void visit(Element element) {
                    final boolean valid = checkInstanceData(element);

                    instanceSatisfiesValidRequired[0] &= valid;

                    if (!valid && indentedLogger.isDebugEnabled()) {
                        indentedLogger.logDebug("", "found invalid element",
                            "element name", Dom4jUtils.elementToDebugString(element));
                    }
                }

                public final void visit(Attribute attribute) {
                    final boolean valid = checkInstanceData(attribute);

                    instanceSatisfiesValidRequired[0] &= valid;

                    if (!valid && indentedLogger.isDebugEnabled()) {
                        indentedLogger.logDebug("", "found invalid attribute",
                            "attribute name", Dom4jUtils.attributeToDebugString(attribute), "parent element", Dom4jUtils.elementToDebugString(attribute.getParent()));
                    }
                }

                private boolean checkInstanceData(Node node) {
                    return InstanceData.getValid(node);
                }
            });
            return instanceSatisfiesValidRequired[0];
        } else {
            // Just check the current node
            return InstanceData.getValid(startNode);
        }
    }

    /**
     * Create an application/x-www-form-urlencoded string, encoded in UTF-8, based on the elements and text content
     * present in an XML document.
     *
     * @param document      document to analyze
     * @param separator     separator character
     * @return              application/x-www-form-urlencoded string
     */
    public static String createWwwFormUrlEncoded(final Document document, final String separator) {

        final StringBuilder sb = new StringBuilder(100);
        document.accept(new VisitorSupport() {
            public final void visit(Element element) {
                // We only care about elements

                final List children = element.elements();
                if (children == null || children.size() == 0) {
                    // Only consider leaves
                    final String text = element.getText();
                    if (text != null && text.length() > 0) {
                        // Got one!
                        final String localName = element.getName();

                        if (sb.length() > 0)
                            sb.append(separator);

                        try {
                            sb.append(URLEncoder.encode(localName, "UTF-8"));
                            sb.append('=');
                            sb.append(URLEncoder.encode(text, "UTF-8"));
                            // TODO: check if line breaks will be correcly encoded as "%0D%0A"
                        } catch (UnsupportedEncodingException e) {
                            // Should not happen: UTF-8 must be supported
                            throw new OXFException(e);
                        }
                    }
                }
            }
        });

        return sb.toString();
    }

    /**
     * Implement support for XForms 1.1 section "11.9.7 Serialization as multipart/form-data".
     *
     * @param document          XML document to submit
     * @return                  MultipartRequestEntity
     */
    public static MultipartEntity createMultipartFormData(final Document document) throws IOException {

        // Visit document
        final MultipartEntity multipartEntity = new MultipartEntity();
        document.accept(new VisitorSupport() {
            public final void visit(Element element) {
                try {
                    // Only care about elements

                    // Only consider leaves i.e. elements without children elements
                    final List children = element.elements();
                    if (children == null || children.size() == 0) {

                        final String value = element.getText();
                        {
                            // Got one!
                            final String localName = element.getName();
                            final QName nodeType = InstanceData.getType(element);

                            if (XMLConstants.XS_ANYURI_QNAME.equals(nodeType)) {
                                // Interpret value as xs:anyURI

                                if (InstanceData.getValid(element) && value.trim().length() > 0) {
                                    // Value is valid as per xs:anyURI
                                    // Don't close the stream here, as it will get read later when the MultipartEntity
                                    // we create here is written to an output stream
                                    addPart(multipartEntity, URLFactory.createURL(value).openStream(), element, value);
                                } else {
                                    // Value is invalid as per xs:anyURI
                                    // Just use the value as is (could also ignore it)
                                    multipartEntity.addPart(localName, new StringBody(value, Charset.forName("UTF-8")));
                                }

                            } else if (XMLConstants.XS_BASE64BINARY_QNAME.equals(nodeType)) {
                                // Interpret value as xs:base64Binary

                                if (InstanceData.getValid(element) && value.trim().length() > 0) {
                                    // Value is valid as per xs:base64Binary
                                    addPart(multipartEntity, new ByteArrayInputStream(NetUtils.base64StringToByteArray(value)), element, null);
                                } else {
                                    // Value is invalid as per xs:base64Binary
                                    // Just use the value as is (could also ignore it)
                                    multipartEntity.addPart(localName, new StringBody(value, Charset.forName("UTF-8")));
                                }
                            } else {
                                // Just use the value as is
                                multipartEntity.addPart(localName, new StringBody(value, Charset.forName("UTF-8")));
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }
        });

        return multipartEntity;
    }

    static private void addPart(MultipartEntity multipartEntity, InputStream inputStream, Element element, String url) {
        // Gather mediatype and filename if known
        // NOTE: special MIP-like annotations were added just before re-rooting/pruning element. Those will be
        // removed during the next recalculate.

        // See this WG action item (which was decided but not carried out): "Clarify that upload activation produces
        // content and possibly filename and mediatype info as metadata. If available, filename and mediatype are copied
        // to instance data if upload filename and mediatype elements are specified. At serialization, filename and
        // mediatype from instance data are used if upload filename and mediatype are specified; otherwise, filename and
        // mediatype are drawn from upload metadata, if they were available at time of upload activation"
        //
        // See:
        // http://lists.w3.org/Archives/Public/public-forms/2009May/0052.html
        // http://lists.w3.org/Archives/Public/public-forms/2009Apr/att-0010/2009-04-22.html#ACTION2
        //
        // See also this clarification:
        // http://lists.w3.org/Archives/Public/public-forms/2009May/0053.html
        // http://lists.w3.org/Archives/Public/public-forms/2009Apr/att-0003/2009-04-01.html#ACTION1
        //
        // The bottom line is that if we can find the xf:upload control bound to a node to submit, we try to get
        // metadata from that control. If that fails (which can be because the control is non-relevant, bound to another
        // control, or never had nested xf:filename/xf:mediatype elements), we try URL metadata. URL metadata is only
        // present on nodes written by xf:upload as temporary file: URLs. It is not present if the data is stored as
        // xs:base64Binary. In any case, metadata can be absent.
        //
        // If an xf:upload control saved data to a node as xs:anyURI, has xf:filename/xf:mediatype elements, is still
        // relevant and bound to the original node (as well as its children elements), and if the nodes pointed to by
        // the children elements have not been modified (e.g. by xf:setvalue), then retrieving the metadata via
        // xf:upload should be equivalent to retrieving it via the URL metadata.
        //
        // Benefits of URL metadata: a single xf:upload can be used to save data to multiple nodes over time, and it
        // doesn't have to be relevant and bound upon submission.
        //
        // Benefits of using xf:upload metadata: it is possible to modify the filename and mediatype subsequently.
        //
        // URL metadata was added 2012-05-29.

        // Get mediatype, first via xf:upload control, or, if not found, try URL metadata
        String mediatype = InstanceData.getTransientAnnotation(element, "xxforms-mediatype");
        if (mediatype == null && url != null)
            mediatype = XFormsUploadControl.getParameterOrNull(url, "mediatype");

        // Get filename, first via xf:upload control, or, if not found, try URL metadata
        String filename = InstanceData.getTransientAnnotation(element, "xxforms-filename");
        if (filename == null && url != null)
            filename = XFormsUploadControl.getParameterOrNull(url, "filename");

        final ContentBody contentBody = new InputStreamBody(inputStream, mediatype, filename);
        multipartEntity.addPart(element.getName(), contentBody);

    }

    /**
     * Annotate the DOM with information about file name and mediatype provided by uploads if available.
     *
     * @param containingDocument    current XFormsContainingDocument
     * @param currentInstance       instance containing the nodes to check
     */
    public static void annotateBoundRelevantUploadControls(XFormsContainingDocument containingDocument, XFormsInstance currentInstance) {
        final XFormsControls xformsControls = containingDocument.getControls();
        final Map<String, XFormsControl> uploadControls = xformsControls.getCurrentControlTree().getUploadControls();
        if (uploadControls != null) {
            for (Object o: uploadControls.values()) {
                final XFormsUploadControl currentControl = (XFormsUploadControl) o;
                if (currentControl.isRelevant()) {
                    final Item controlBoundItem = currentControl.getBoundItem();
                    if (controlBoundItem instanceof NodeInfo) {
                        final NodeInfo controlBoundNodeInfo = (NodeInfo) controlBoundItem;
                        if (currentInstance == currentInstance.model().getInstanceForNode(controlBoundNodeInfo)) {
                            // Found one relevant upload control bound to the instance we are submitting
                            // NOTE: special MIP-like annotations were added just before re-rooting/pruning element. Those
                            // will be removed during the next recalculate.
                            final String fileName = currentControl.boundFilename();
                            if (fileName != null) {
                                InstanceData.setTransientAnnotation(controlBoundNodeInfo, "xxforms-filename", fileName);
                            }
                            final String mediatype = currentControl.boundFileMediatype();
                            if (mediatype != null) {
                                InstanceData.setTransientAnnotation(controlBoundNodeInfo, "xxforms-mediatype", mediatype);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns whether there is at least one relevant upload control with pending upload bound to any node of the given instance.
     *
     * @param containingDocument    current XFormsContainingDocument
     * @param currentInstance       instance to check
     * @return                      true iif condition is satisfied
     */
    public static boolean hasBoundRelevantPendingUploadControls(XFormsContainingDocument containingDocument, XFormsInstance currentInstance) {
        if (containingDocument.countPendingUploads() > 0) { // don't bother if there is no pending upload
            final XFormsControls xformsControls = containingDocument.getControls();
            final Map uploadControls = xformsControls.getCurrentControlTree().getUploadControls();
            if (uploadControls != null) {
                for (Object o: uploadControls.values()) {
                    final XFormsUploadControl currentControl = (XFormsUploadControl) o;
                    if (currentControl.isRelevant() && containingDocument.isUploadPendingFor(currentControl)) {
                        final Item controlBoundItem = currentControl.getBoundItem();
                        if (controlBoundItem instanceof NodeInfo && currentInstance == currentInstance.model().getInstanceForNode((NodeInfo) controlBoundItem)) {
                            // Found one relevant upload control with pending upload bound to the instance we are submitting
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}

class ResponseAdapter implements ExternalContext.Response {

    private ExternalContext.Response response;

    private int status = 200;
    private String contentType;

    private StringBuilderWriter stringWriter;
    private PrintWriter printWriter;
    private ResponseAdapter.LocalByteArrayOutputStream byteStream;

    private InputStream inputStream;

    public ResponseAdapter(ExternalContext.Response response) {
        this.response = response;
    }

    public int getResponseCode() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public InputStream getInputStream() {
        if (inputStream == null) {
            if (stringWriter != null) {
                final byte[] bytes;
                try {
                    bytes = stringWriter.getBuilder().toString().getBytes("utf-8");
                } catch (UnsupportedEncodingException e) {
                    throw new OXFException(e); // should not happen
                }
                inputStream = new ByteArrayInputStream(bytes, 0, bytes.length);
//                throw new OXFException("ResponseAdapter.getInputStream() does not yet support content written with getWriter().");
            } else if (byteStream != null) {
                inputStream = new ByteArrayInputStream(byteStream.getByteArray(), 0, byteStream.size());
            }
        }

        return inputStream;
    }

    public void addHeader(String name, String value) {
    }

    public boolean checkIfModifiedSince(long lastModified) {
        return true;
    }

    public String getCharacterEncoding() {
        return null;
    }

    public String getNamespacePrefix() {
        return null;
    }

    public OutputStream getOutputStream() throws IOException {
        if (byteStream == null)
            byteStream = new ResponseAdapter.LocalByteArrayOutputStream();
        return byteStream;
    }

    public PrintWriter getWriter() throws IOException {
        if (stringWriter == null) {
            stringWriter = new StringBuilderWriter();
            printWriter = new PrintWriter(stringWriter);
        }
        return printWriter;
    }

    public boolean isCommitted() {
        return false;
    }

    public void reset() {
    }

    public String rewriteActionURL(String urlString) {
        return response.rewriteActionURL(urlString);
    }

    public String rewriteRenderURL(String urlString) {
        return response.rewriteRenderURL(urlString);
    }

    public String rewriteActionURL(String urlString, String portletMode, String windowState) {
        return response.rewriteActionURL(urlString,  portletMode, windowState);
    }

    public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
        return response.rewriteRenderURL(urlString, portletMode, windowState);
    }

    public String rewriteResourceURL(String urlString, int rewriteMode) {
        return response.rewriteResourceURL(urlString, rewriteMode);
    }

    public void sendError(int sc) throws IOException {
        this.status = sc;
    }

    public void sendRedirect(String location, boolean isServerSide, boolean isExitPortal) throws IOException {
    }

    public void setPageCaching(long lastModified) {
    }

    public void setResourceCaching(long lastModified, long expires) {
    }

    public void setContentLength(int len) {
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setHeader(String name, String value) {
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setTitle(String title) {
    }

    private static class LocalByteArrayOutputStream extends ByteArrayOutputStream {
        public byte[] getByteArray() {
            return buf;
        }
    }

    public Object getNativeResponse() {
        throw new OXFException("NIY");
    }
}