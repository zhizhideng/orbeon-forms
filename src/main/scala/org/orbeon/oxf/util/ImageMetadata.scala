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
package org.orbeon.oxf.util

import collection.JavaConverters._
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.bmp.BmpHeaderDirectory
import com.drew.metadata.gif.GifHeaderDirectory
import com.drew.metadata.jpeg.JpegDirectory
import com.drew.metadata.png.PngDirectory
import java.io.InputStream
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.saxon.om.Item
import org.orbeon.scaxon.XML._

// Functions to extract image metadata from a stream
object ImageMetadata {

    // Given a name in KnownNames, try to extract its value and return it as a Saxon Item
    def findKnownMetadata(content: InputStream, name: String): Option[Item] =
        KnownNamesToMetadataExtractorNames.get(name) flatMap (findMetadata(content, _)) map anyToItem

    // Try to find the type of the image
    def findImageMediatype(content: InputStream) = {
        val metadata = useAndClose(content)(ImageMetadataReader.readMetadata)

        // Support formats thar are supported by web browsers only
        // http://en.wikipedia.org/wiki/Comparison_of_web_browsers#Image_format_support
        metadata.getDirectories.asScala.iterator collectFirst {
            case _: JpegDirectory      ⇒ "image/jpeg"
            case _: PngDirectory       ⇒ "image/png"
            case _: GifHeaderDirectory ⇒ "image/gif"
            case _: BmpHeaderDirectory ⇒ "image/bmp"
        }
    }

    // Try to extract the value of the given metadata item
    def findMetadata(content: InputStream, name: String): Option[AnyRef] = {

        val metadata = useAndClose(content)(ImageMetadataReader.readMetadata)

        val directoryIterator =
            for {
                directory ← metadata.getDirectories.asScala.iterator
                tags      = for (tag ← directory.getTags.asScala)
                                yield
                                    tag.getTagName → tag.getTagType
            } yield
                directory → tags.toMap

        directoryIterator collectFirst {
            case (directory, map) if map.contains(name) ⇒ directory.getObject(map(name))
        }
    }

    private val KnownNamesToMetadataExtractorNames = Map(
        "width"  → "Image Width",
        "height" → "Image Height"
    )

    // All known names
    val KnownNames = KnownNamesToMetadataExtractorNames.keySet
}
