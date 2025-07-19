package util

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.Level

import scala.jdk.CollectionConverters.CollectionHasAsScala

class ColoredLevelConverter extends ClassicConverter {

  private val GRAY_FG = "\u001b[37m"
  private val CYAN_FG = "\u001b[36m"
  private val YELLOW_FG = "\u001b[33m"
  private val RED_FG = "\u001b[31m"
  private val DEFAULT_FG = "\u001b[0m"
  private val BLUE_FG = "\u001b[34m"

  override def convert(event: ILoggingEvent): String = {
    val level = event.getLevel
    val formattedMsg = {
      val timestamp = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date(event.getTimeStamp))
      // Main program starting the analysis isn't multithreaded, therefore thread name omitted
      //val thread = event.getThreadName
      val levelStr = f"${level.toString}%-5s"
      val logger = event.getLoggerName
      val msg = event.getFormattedMessage
      s"[$timestamp] $levelStr [$logger] $msg"
    }
    val markers = event.getMarkerList

    val color = level match {
      case Level.ERROR => RED_FG
      case Level.WARN  => YELLOW_FG
      // Info text should sometimes be highlighted in blue, specified via a specific marker.
      case Level.INFO  =>
        if (markers != null && markers.asScala.exists(marker => marker.getName == "BLUE")) BLUE_FG
        else DEFAULT_FG
      case Level.DEBUG => CYAN_FG
      case Level.TRACE => GRAY_FG
      case _           => DEFAULT_FG
    }

    s"$color$formattedMsg$DEFAULT_FG"
  }
}
