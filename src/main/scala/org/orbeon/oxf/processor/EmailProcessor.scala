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
package org.orbeon.oxf.processor

import EmailProcessor._
import collection.JavaConverters._
import java.io._
import java.util.{Properties ⇒ JProperties}
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.mail.Message.RecipientType
import javax.mail._
import javax.mail.internet._
import javax.xml.transform.OutputKeys
import javax.xml.transform.sax.SAXSource
import javax.xml.transform.stream.StreamResult
import org.apache.commons.fileupload.FileItem
import org.dom4j.{Node, Document, Element}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.URLGenerator
import org.orbeon.oxf.processor.serializer.BinaryTextXMLReceiver
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom4j._
import org.xml.sax._

/**
 * This processor allows sending emails. It supports multipart messages and inline as well as out-of-line attachments.
 *
 * For some useful JavaMail information: http://java.sun.com/products/javamail/FAQ.html
 *
 * TODO:
 *
 * o revise support of text/html
 * o built-in support for HTML could handle src="cid:*" with part/message ids
 * o support text/xml? or just XHTML?
 * o build message with SAX, not DOM, so streaming of input is possible [not necessarily a big win]
 */
class EmailProcessor extends ProcessorImpl {


    addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA, ConfigNamespaceURI))

    override def start(pipelineContext: PipelineContext) {

        val dataDocument   = readInputAsDOM4J(pipelineContext, ProcessorImpl.INPUT_DATA)
        val messageElement = dataDocument.getRootElement

        // Get system id (will likely be null if document is generated dynamically)
        val dataInputSystemId = Option(messageElement.getData) map (_.asInstanceOf[LocationData].getSystemID) orNull

        implicit val propertySet = getPropertySet

        // Set SMTP host
        val properties = new JProperties
        val host =
            nonEmptyOrNone(propertySet.getString(TestSMTPHost))  orElse
            valueFromElementOrProperty(messageElement, SMTPHost) getOrElse
            (throw new OXFException("Could not find SMTP host in configuration or in properties"))

        properties.setProperty("mail.smtp.host", host)

        // Create session
        val session = {

            // Get credentials if any
            val (usernameOption, passwordOption) = {
                Option(messageElement.element("credentials")) match {
                    case Some(credentials) ⇒
                        val usernameElement = credentials.element(Username)
                        val passwordElement = credentials.element(Password)

                        (optionalValueTrim(usernameElement), optionalValueTrim(passwordElement))
                    case None ⇒
                        (nonEmptyOrNone(propertySet.getString(Username)), nonEmptyOrNone(propertySet.getString(Password)))
                }
            }

            def ensureCredentials(encryption: String) =
                if (usernameOption.isEmpty)
                    throw new OXFException("Credentails are required when using " + encryption.toUpperCase)

            val defaultUpdatePort: String ⇒ Unit =
                properties.setProperty("mail.smtp.port", _)

            // SSL and TLS
            val (defaultPort, updatePort) =
                valueFromElementOrProperty(messageElement, Encryption) match {
                    case Some("ssl") ⇒
                        ensureCredentials("ssl") // partly enforced by the schema, but could have been blank

                        properties.setProperty("mail.smtp.auth", "true")
                        properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")

                        val updatePort: String ⇒ Unit = { port ⇒
                            properties.setProperty("mail.smtp.socketFactory.port", port)
                            defaultUpdatePort(port)
                        }

                        // Should we change the default to 587?
                        // "Although some servers support port 465 for legacy secure SMTP in violation of the
                        // specifications" http://en.wikipedia.org/wiki/Simple_Mail_Transfer_Protocol#Ports
                        (Some("465"), updatePort)

                    case Some("tls") ⇒
                        ensureCredentials("tls") // partly enforced by the schema, but could have been blank

                        properties.setProperty("mail.smtp.auth", "true")
                        properties.setProperty("mail.smtp.starttls.enable", "true")

                        (Some("587"), defaultUpdatePort)

                    case _ ⇒
                        (None, defaultUpdatePort)
                }

            // Set or override port depending on the encryption settings
            valueFromElementOrProperty(messageElement, SMTPPort) orElse defaultPort foreach updatePort

            usernameOption match {
                case Some(username) ⇒
                    if (Logger.isInfoEnabled) Logger.info("Authentication")

                    properties.setProperty("mail.smtp.auth", "true")

                    if (Logger.isInfoEnabled) Logger.info("Username: " + usernameOption)

                    Session.getInstance(properties, new Authenticator {
                        override def getPasswordAuthentication: PasswordAuthentication = {
                            new PasswordAuthentication(username, passwordOption getOrElse "")
                        }
                    })
                case None ⇒
                    if (Logger.isInfoEnabled) Logger.info("No Authentication")
                    Session.getInstance(properties)
            }
        }

        // Create message
        val message = new MimeMessage(session)

        def createAddresses(addressElement: Element): Array[Address] = {
            val email = addressElement.element("email").getTextTrim // required

            val result = Option(addressElement.element("name")) match {
                case Some(nameElement) ⇒ Seq(new InternetAddress(email, nameElement.getTextTrim))
                case None              ⇒ InternetAddress.parse(email).toList
            }

            result.toArray
        }

        def addRecipients(elementName: String, recipientType: RecipientType) =
            for (element ← Dom4jUtils.elements(messageElement, elementName).asScala) {
                val addresses = createAddresses(element)
                message.addRecipients(recipientType, addresses)
            }

        // Set From
        message.addFrom(createAddresses(messageElement.element("from")))

        // Set To
        nonEmptyOrNone(propertySet.getString(TestTo)) match {
            case Some(testTo) ⇒
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(testTo))
            case None ⇒
                addRecipients("to", Message.RecipientType.TO)
        }

        addRecipients("cc", Message.RecipientType.CC)
        addRecipients("bcc", Message.RecipientType.BCC)

        // Set headers if any
        for (headerElement ← Dom4jUtils.elements(messageElement, "header").asScala) {
            val headerName  = headerElement.element("name").getTextTrim  // required
            val headerValue = headerElement.element("value").getTextTrim // required

            // NOTE: Use encodeText() in case there are non-ASCII characters
            message.addHeader(headerName, MimeUtility.encodeText(headerValue, DEFAULT_CHARACTER_ENCODING, null))
        }

        // Set the email subject
        // The JavaMail spec is badly written and is not clear about whether this needs to be done here. But it
        // seems to use the platform's default charset, which we don't want to deal with. So we preemptively encode.
        // The result is pure ASCII so that setSubject() will not attempt to re-encode it.
        message.setSubject(MimeUtility.encodeText(messageElement.element("subject").getStringValue, DEFAULT_CHARACTER_ENCODING, null))

        // Handle body
        val textElement = messageElement.element("text")
        val bodyElement = messageElement.element("body")
        if (textElement ne null)
            // Old deprecated mechanism (simple text body)
            message.setText(textElement.getStringValue)
        else if (bodyElement ne null)
            // New mechanism with body and parts
            handleBody(pipelineContext, dataInputSystemId, message, bodyElement)
        else
            throw new OXFException("Main text or body element not found")

        // Send message
        useAndClose(session.getTransport("smtp")) { transport ⇒
            Transport.send(message)
        }
    }

    private def handleBody(pipelineContext: PipelineContext, dataInputSystemId: String, parentPart: Part, bodyElement: Element) {

        // Find out if there are embedded parts
        val parts = bodyElement.elementIterator("part")
        val multipartOption =
            if (bodyElement.getName == "body") {
                val bodyMultipart = Option(bodyElement.attributeValue("mime-multipart"))

                if (parts.hasNext)
                    bodyMultipart orElse Some("mixed")
                else if (bodyMultipart.isDefined)
                    throw new OXFException("mime-multipart attribute on body element requires part children elements")
                else
                    None
            } else {
                Option(NetUtils.getContentTypeMediaType(bodyElement.attributeValue("content-type"))) filter
                (_.startsWith("multipart/")) map
                (_.substring("multipart/".length))
            }

        multipartOption match {
            case Some(multipart) ⇒
                // Multipart content is requested
                val mimeMultipart = new MimeMultipart(multipart)
                while (parts.hasNext) {
                    val partElement = parts.next.asInstanceOf[Element]
                    val mimeBodyPart = new MimeBodyPart
                    handleBody(pipelineContext, dataInputSystemId, mimeBodyPart, partElement)
                    mimeMultipart.addBodyPart(mimeBodyPart)
                }

                // Set content on parent part
                parentPart.setContent(mimeMultipart)
            case None ⇒
                // No multipart, just use the content of the element and add to the current part (which can be the main message)
                handlePart(pipelineContext, dataInputSystemId, parentPart, bodyElement)
        }
    }

    private def handlePart(pipelineContext: PipelineContext, dataInputSystemId: String, parentPart: Part, partOrBodyElement: Element) {
        val name = partOrBodyElement.attributeValue("name")
        val contentTypeAttribute = partOrBodyElement.attributeValue("content-type")
        val contentType = NetUtils.getContentTypeMediaType(contentTypeAttribute)
        val charset = Option(NetUtils.getContentTypeCharset(contentTypeAttribute)) getOrElse DEFAULT_CHARACTER_ENCODING

        val contentTypeWithCharset = contentType + "; charset=" + charset

        // Either a String or a FileItem
        val content =
            Option(partOrBodyElement.attributeValue("src")) match {
                case Some(src) ⇒
                    // Content of the part is not inline

                    // Generate a FileItem from the source
                    val source = getSAXSource(EmailProcessor.this, pipelineContext, src, dataInputSystemId, contentType)
                    Left(handleStreamedPartContent(pipelineContext, source))
                case None ⇒
                    // Content of the part is inline

                    // In the cases of text/html and XML, there must be exactly one root element
                    val needsRootElement = contentType == "text/html"// || ProcessorUtils.isXMLContentType(contentType);
                    if (needsRootElement && partOrBodyElement.elements.size != 1)
                        throw new ValidationException("The <body> or <part> element must contain exactly one element for text/html", partOrBodyElement.getData.asInstanceOf[LocationData])

                    // Create Document and convert it into a String
                    val rootElement = (if (needsRootElement) partOrBodyElement.elements.get(0) else partOrBodyElement).asInstanceOf[Element]
                    val partDocument = new NonLazyUserDataDocument
                    partDocument.setRootElement(rootElement.asInstanceOf[NonLazyUserDataElement].clone.asInstanceOf[Element])
                    Right(handleInlinePartContent(partDocument, contentType))
            }

        if (! XMLUtils.isTextOrJSONContentType(contentType)) {
            // This is binary content (including application/xml)
            content match {
                case Left(fileItem) ⇒
                    parentPart.setDataHandler(new DataHandler(new ReadonlyDataSource {
                        def getContentType = contentType
                        def getInputStream = fileItem.getInputStream
                        def getName = name
                    }))
                case Right(inline) ⇒
                    val data = NetUtils.base64StringToByteArray(inline)
                    parentPart.setDataHandler(new DataHandler(new SimpleBinaryDataSource(name, contentType, data)))
            }
        } else {
            // This is text content (including text/xml)
            content match {
                case Left(fileItem) ⇒
                    parentPart.setDataHandler(new DataHandler(new ReadonlyDataSource {
                        // This always contains a charset
                        def getContentType = contentTypeWithCharset
                        // This is encoded with the appropriate charset (user-defined, or the default)
                        def getInputStream = fileItem.getInputStream
                        def getName = name
                    }))
                case Right(inline) ⇒
                    parentPart.setDataHandler(new DataHandler(new SimpleTextDataSource(name, contentTypeWithCharset, inline)))
            }
        }

        // Set content-disposition header
        Option(partOrBodyElement.attributeValue("content-disposition")) foreach
            (contentDisposition ⇒ parentPart.setDisposition(contentDisposition))

        // Set content-id header
        Option(partOrBodyElement.attributeValue("content-id")) foreach
            (contentId ⇒ parentPart.setHeader("content-id", "<" + contentId + ">"))
        //part.setContentID(contentId);
    }

    private def handleInlinePartContent(document: Document, contentType: String) =
        if (contentType == "text/html") {
            // Convert XHTML into an HTML String
            val writer = new StringBuilderWriter
            val identity = TransformerUtils.getIdentityTransformerHandler
            identity.getTransformer.setOutputProperty(OutputKeys.METHOD, "html")
            identity.setResult(new StreamResult(writer))
            val locationSAXWriter = new LocationSAXWriter
            locationSAXWriter.setContentHandler(identity)
            locationSAXWriter.write(document.asInstanceOf[Node])
            writer.toString
        } else
            // For other types, just return the text nodes
            document.getStringValue
}

object EmailProcessor {

    val Logger = LoggerFactory.createLogger(classOf[EmailProcessor])

    val SMTPHost     = "smtp-host"
    val SMTPPort     = "smtp-port"
    val Username     = "username"
    val Password     = "password"
    val Encryption   = "encryption"

    val TestTo       = "test-to"
    val TestSMTPHost = "test-smtp-host"

    val ConfigNamespaceURI = "http://www.orbeon.com/oxf/email"

    // Use utf-8 as most email clients support it. This allows us not to have to pick an inferior encoding.
    val DEFAULT_CHARACTER_ENCODING = "utf-8"

    // Get Some(trimmed value of the element) or None if the element is null
    def optionalValueTrim(e: Element) = nonEmptyOrNone(Option(e) map(_.getStringValue) orNull)

    // First try to get the value from a child element, then from the properties
    def valueFromElementOrProperty(e: Element, name: String)(implicit propertySet: PropertySet) =
        optionalValueTrim(e.element(name)) orElse
        nonEmptyOrNone(propertySet.getString(name))

    // Read a text or binary document and return it as a FileItem
    def handleStreamedPartContent(pipelineContext: PipelineContext, source: SAXSource): FileItem = {
        val fileItem = NetUtils.prepareFileItem(NetUtils.REQUEST_SCOPE)
        TransformerUtils.sourceToSAX(source, new BinaryTextXMLReceiver(fileItem.getOutputStream))
        fileItem
    }

    def getSAXSource(processor: Processor, pipelineContext: PipelineContext, href: String, base: String, contentType: String): SAXSource = {
        val processorOutput =
            Option(ProcessorImpl.getProcessorInputSchemeInputName(href)) match {
                case Some(inputName) ⇒
                    processor.getInputByName(inputName).getOutput
                case None ⇒
                    val urlGenerator =
                        Option(contentType) map
                        (new URLGenerator(URLFactory.createURL(base, href), _, true)) getOrElse
                         new URLGenerator(URLFactory.createURL(base, href))

                    urlGenerator.createOutput(ProcessorImpl.OUTPUT_DATA)
            }

        val saxSource = new SAXSource(new ProcessorOutputXMLReader(pipelineContext, processorOutput), new InputSource)
        saxSource.setSystemId(href)
        saxSource
    }

    private abstract class ReadonlyDataSource extends DataSource {
        def getOutputStream = throw new IOException("Write operation not supported")
    }

    private class SimpleTextDataSource(val getName: String, val getContentType: String, text: String) extends ReadonlyDataSource {
        def getInputStream  = new ByteArrayInputStream(text.getBytes("utf-8"))
    }

    private class SimpleBinaryDataSource(val getName: String, val getContentType: String, data: Array[Byte]) extends ReadonlyDataSource {
        def getInputStream  = new ByteArrayInputStream(data)
    }
}
