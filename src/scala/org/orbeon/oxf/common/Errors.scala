/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.common

import collection.JavaConverters._
import collection.mutable.ListBuffer
import org.apache.commons.lang.StringUtils.isNotBlank
import org.orbeon.oxf.xml.dom4j.{ExtendedLocationData, LocationData}

// Exception formatting
object Errors {

    private val Width = 120
    private val MaxStackLength = 40

    private val OuterHr  = '+' + "-" * (Width - 2) + '+'
    private val InnerHr  = withBorder("-" * (Width - 2))
    private val DottedHr = withBorder("---8<-----" * ((Width - 2 + 9) / 10), Width)

    // Nicely format an exception into a String printable in a log file
    def format(throwable: Throwable): String = {

        // All nested errors from caused to cause
        val errors = {
            var currentThrowable = throwable

            val result = ListBuffer[Error]()

            while (currentThrowable ne null) {
                result += Error(currentThrowable)
                currentThrowable = OXFException.getNestedThrowable(currentThrowable)
            }

            result.toList
        }

        // Top-level message
        val message = errors.last.message getOrElse "[No error message provided.]"

        val firstThrowableWithLocation = errors find (_.location.nonEmpty)
        val locations = firstThrowableWithLocation.toList flatMap (_.location)

        def formattedJavaTrace(e: Error) =
            e.stackTrace map (_.formatted(Width))

        def formattedDropCaused(causedTrace: Option[List[String]], e: Error) = {
            val newTrace = formattedJavaTrace(e)
            causedTrace match {
                case Some(causedTrace) ⇒
                    val commonSize = causedTrace zip newTrace takeWhile (pair ⇒ pair._1 == pair._2) size
                    val toShow = newTrace drop commonSize

                    def truncate(max: Int) =
                        if (toShow.size <= max + max / 10) // give it 10% tolerance
                            toShow
                        else
                            (toShow take max / 2) ::: List(DottedHr) ::: (toShow takeRight max / 2)

                    (newTrace, truncate(MaxStackLength))
                case none ⇒
                    (newTrace, newTrace)
            }
        }

        def allFormattedJavaTraces: List[String] = {

            def nextTraces(causedTrace: Option[List[String]], rest: List[Error]): List[String] = rest headOption match {
                case Some(error) ⇒
                    val (newTrace, newTraceCompact) = formattedDropCaused(causedTrace, error)

                    nextTraces(Some(newTrace), rest.tail) :::
                    InnerHr ::
                    withBorder("Exception: " + error.className, 120) ::
                    InnerHr ::
                    newTraceCompact.reverse

                case None ⇒
                    Nil
            }

            nextTraces(None, errors)
        }

        val lines =
            OuterHr ::
            withBorder("An Error has Occurred in Orbeon Forms", Width) ::
            InnerHr ::
            withBorder(message, Width) ::
            InnerHr ::
            withBorder("Orbeon Forms Call Stack", Width) ::
            InnerHr ::
            (locations map (_.formatted(Width))) :::
            allFormattedJavaTraces :::
            OuterHr ::
            Nil

        "\n" + (lines mkString "\n")
    }

    // An error (throwable) with optional message, location and trace
    private case class Error(className: String, message: Option[String], location: List[SourceLocation], stackTrace: List[JavaStackEntry])

    private object Error {
        // Create from Throwable
        def apply(throwable: Throwable): Error =
            Error(throwable.getClass.getName,
                Option(getThrowableMessage(throwable)) filter (isNotBlank(_)),
                ValidationException.getAllLocationData(throwable).asScala.toList flatMap (SourceLocation(_)),
                throwable.getStackTrace.reverseIterator map (JavaStackEntry(_)) toList)

        private def getThrowableMessage(throwable: Throwable) = throwable match {
            case ve: ValidationException ⇒ ve.getSimpleMessage
            case t ⇒ t.getMessage
        }
    }

    // A source location in a file
    private case class SourceLocation(file: String, line: Option[Int], col: Option[Int], description: Option[String], params: List[(String, String)]) {

        require(file ne null)

        def key = file + '-' + line + '-' + line

        // Format as string
        def formatted(width: Int) = {
            val fixed = paddedInt(line, 4) :: paddedInt(col, 4) :: padded(description, 50) :: Nil
            val remainder = (fixed.foldLeft(0)(_ + _.size)) + fixed.size + 2

            ("" :: padded(Some(file), width - remainder) :: fixed ::: "" :: Nil) mkString "|"
        }
    }

    private object SourceLocation {
        // Create from LocationData
        def apply(locationData: LocationData): Option[SourceLocation] =

            if (isNotBlank(locationData.getSystemID) && ! locationData.getSystemID.endsWith(".java")) {
                val (description, params) =
                    locationData match {
                        case extended: ExtendedLocationData ⇒
                            (Option(extended.getDescription), arrayToTuples(extended.getParameters))
                        case _ ⇒ (None, Nil)
                    }

                Some(SourceLocation(locationData.getSystemID, filterLineCol(locationData.getLine), filterLineCol(locationData.getCol), description, params))
            } else
                None

        private def arrayToTuples(a: Array[String]): List[(String, String)] = Option(a) match {
            case Some(a) ⇒ a.grouped(2) map (sub ⇒ (sub(0), sub(1))) filter (_._2 ne null) toList
            case None    ⇒ Nil
        }
    }

    // A Java stack entry with optional file and line
    private case class JavaStackEntry(className: String, method: String, file: Option[String], line: Option[Int]) {
        // Format as string
        def formatted(width: Int) = {
            val fixed = padded(Option(method), 30) :: padded(file, 30) :: paddedInt(line, 4) :: Nil
            val remainder = (fixed.foldLeft(0)(_ + _.size)) + fixed.size + 2

            ("" :: padded(Some(className), width - remainder) :: fixed ::: "" :: Nil) mkString "|"
        }
    }

    private object JavaStackEntry {
        def apply(element: StackTraceElement): JavaStackEntry =
            JavaStackEntry(element.getClassName, element.getMethodName, Option(element.getFileName), filterLineCol(element.getLineNumber))
    }

    private def filterLineCol(i: Int) = Option(i) filter (_ > -1)
    private def withBorder(s: String, width: Int): String = s split "\n" map (line ⇒ withBorder(padded(Some(line), width - 2))) mkString "\n"
    private def withBorder(s: String): String = '|' + s + '|'
    private def padded(s: Option[String], len: Int): String = s.getOrElse("").padTo(len, ' ').substring(0, len)
    private def paddedInt(i: Option[Int],    len: Int): String = padded(Some(i.getOrElse("").toString.reverse), len).reverse
}
