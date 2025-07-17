package util

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.Level

class ColoredLevelConverter extends ClassicConverter {

  private val GRAY_FG = "\u001b[37m"
  private val CYAN_FG = "\u001b[36m"
  private val YELLOW_FG = "\u001b[33m"
  private val RED_FG = "\u001b[31m"
  private val DEFAULT_FG = "\u001b[0m"

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

    val color = level match {
      case Level.ERROR => RED_FG
      case Level.WARN  => YELLOW_FG
      case Level.INFO  => DEFAULT_FG
      case Level.DEBUG => CYAN_FG
      case Level.TRACE => GRAY_FG
      case _           => DEFAULT_FG
    }

    if (color.nonEmpty)
      s"$color$formattedMsg$DEFAULT_FG"
    else
      formattedMsg
  }
}
