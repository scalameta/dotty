package dotty.tools
package dotc
package reporting

import core.Contexts.Context
import core.Decorators._
import printing.Highlighting.{Blue, Red}
import diagnostic.{Message, MessageContainer, NoExplanation}
import diagnostic.messages._
import util.SourcePosition

import scala.collection.mutable

trait MessageRendering {
  def stripColor(str: String): String =
    str.replaceAll("\u001B\\[[;\\d]*m", "")

  def sourceLines(pos: SourcePosition)(implicit ctx: Context): (List[String], List[String], Int) = {
    var maxLen = Int.MinValue
    def render(xs: List[Int]) =
      xs.map(pos.source.offsetToLine(_))
        .map { lineNbr =>
          val prefix = s"${lineNbr + 1} |"
          maxLen = math.max(maxLen, prefix.length)
          (prefix, pos.lineContent(lineNbr).stripLineEnd)
        }
        .map { case (prefix, line) =>
          val lnum = Red(" " * math.max(0, maxLen - prefix.length) + prefix)
          hl"$lnum$line"
        }

    val (before, after) = pos.beforeAndAfterPoint
    (render(before), render(after), maxLen)
  }

  def columnMarker(pos: SourcePosition, offset: Int)(implicit ctx: Context): String = {
    val prefix = " " * (offset - 1)
    val whitespace = " " * pos.startColumn
    val carets = Red {
      if (pos.startLine == pos.endLine)
        "^" * math.max(1, pos.endColumn - pos.startColumn)
      else "^"
    }

    s"$prefix|$whitespace${carets.show}"
  }

  def errorMsg(pos: SourcePosition, msg: String, offset: Int)(implicit ctx: Context): String = {
    val leastWhitespace = msg.lines.foldLeft(Int.MaxValue) { (minPad, line) =>
      val lineLength = stripColor(line).length
      val padding =
        math.min(math.max(0, ctx.settings.pageWidth.value - offset - lineLength), offset + pos.startColumn)

      if (padding < minPad) padding
      else minPad
    }

    msg.lines
      .map { line => " " * (offset - 1) + "|" + (" " * (leastWhitespace - offset)) + line }
      .mkString(sys.props("line.separator"))
  }

  def posStr(pos: SourcePosition, diagnosticLevel: String, message: Message)(implicit ctx: Context): String =
    if (pos.exists) Blue({
      val file = pos.source.file.toString
      val errId =
        if (message.errorId != NoExplanation.ID)
          s"[E${"0" * (3 - message.errorId.toString.length) + message.errorId}] "
        else ""
      val kind =
        if (message.kind == "") diagnosticLevel
        else s"${message.kind} $diagnosticLevel"
      val prefix = s"-- ${errId}${kind}: $file "

      prefix +
        ("-" * math.max(ctx.settings.pageWidth.value - stripColor(prefix).length, 0))
    }).show else ""

  def explanation(m: Message)(implicit ctx: Context): String = {
    val sb = new StringBuilder(hl"""|
                                    |${Blue("Explanation")}
                                    |${Blue("===========")}""".stripMargin)
    sb.append('\n').append(m.explanation)
    if (m.explanation.lastOption != Some('\n')) sb.append('\n')
    sb.toString
  }

  def messageAndPos(msg: Message, pos: SourcePosition, diagnosticLevel: String)(implicit ctx: Context): String = {
    val sb = mutable.StringBuilder.newBuilder
    sb.append(posStr(pos, diagnosticLevel, msg)).append('\n')
    if (pos.exists) {
      val (srcBefore, srcAfter, offset) = sourceLines(pos)
      val marker = columnMarker(pos, offset)
      val err = errorMsg(pos, msg.msg, offset)
      sb.append((srcBefore ::: marker :: err :: srcAfter).mkString("\n"))
    } else sb.append(msg.msg)
    sb.toString
  }

  def diagnosticLevel(cont: MessageContainer): String = {
    cont match {
      case m: Error => "Error"
      case m: FeatureWarning => "Feature Warning"
      case m: DeprecationWarning => "Deprecation Warning"
      case m: UncheckedWarning => "Unchecked Warning"
      case m: MigrationWarning => "Migration Warning"
      case m: Warning => "Warning"
      case m: Info => "Info"
    }
  }
}
