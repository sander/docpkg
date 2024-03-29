#!/usr/bin/env -S scala-cli --cli-version 0.1.20 shebang

//> using jvm "temurin:1.17"
//> using scala "3.2.2"

import java.io.FileFilter
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.nio.file.Path
import scala.io.Source
import scala.util.Using

val extension = "md"
val indexName = s"README.$extension"
val sectionStart = "<!-- Start autogenerated index -->"
val sectionEnd = "<!-- End autogenerated index -->"

if (args.length != 1)
  println("Provide a directory name as argument.")
  sys.exit(1)

val directory = Path.of(args(0))

extension (f: File)
  def relativize: Path = directory.relativize(f.toPath)
  def isNonIndexDocument: Boolean =
    f.toString.endsWith(s".$extension")
    && !f.toString.endsWith(s"${File.separator}$indexName")

extension (p: Path)
  def list(filter: FileFilter): List[Path] =
    p.toFile.listFiles(filter).toList.map(_.toPath)
  def getIndex = p.resolve(indexName) match
    case p if p.toFile.isFile => Some(p)
    case _                    => None

opaque type Content = List[String]
object Content:
  def read(p: Path): Content =
    Source.fromFile(p.toFile).getLines.toList
extension (c: Content)
  def findPrefixed(prefix: String): Option[String] =
    c.find(_.startsWith(prefix)).map(_.substring(prefix.length))

opaque type Title = String
object Title:
  def apply(content: List[String]): Title =
    content
      .findPrefixed("# ")
      .orElse(content.findPrefixed("title: "))
      .orElse(content.headOption)
      .getOrElse("Untitled")
extension (t: Title) def escape: String = t.replaceAllLiterally("]", "\\]")

case class Document(path: Path, title: Title)
object Document:
  def read(p: Path): Document = Document(p, Title(Content.read(p)))

case class Node(document: Document, children: List[Node]):
  override def toString(): String =
    val title = document.title.escape
    val url = directory.relativize(document.path)
    val head = s"- [$title]($url)"
    val tail = children.map("    " + _.toString.indent(4).trim())
    (head :: tail).mkString("\n")
object Node:
  def apply(d: Document): Node = Node(d, List.empty)

opaque type Traversal = Option[Node]

def traverse(path: Path): Traversal =
  val documents = path.list(_.isNonIndexDocument)
  val directories = path.list(_.isDirectory)
  (path.getIndex, directories.flatMap(traverse(_).toList)) match
    case (Some(p), directoryNodes) =>
      val content = Content.read(p)
      Some(
        Node(
          Document(p, Title(content)),
          if (!path.equals(directory) && content.contains(sectionStart))
            List.empty
          else
            directoryNodes.sortBy(_.document.path) ++ documents
              .map(Document.read)
              .map(Node.apply)
              .sortBy(_.document.path)
        )
      )
    case (None, directoryNode :: Nil) => Some(directoryNode)
    case (None, _)                    => None

val traversal = traverse(directory).getOrElse {
  println("No files found.")
  sys.exit(1)
}

val readmeContent = Content.read(traversal.document.path)

val newContent =
  readmeContent.takeWhile(_ != sectionStart)
    ++ List(sectionStart)
    ++ traversal.children.map(_.toString)
    ++ readmeContent.dropWhile(_ != sectionEnd)

val fileWriter = FileWriter(traversal.document.path.toFile)

Using(BufferedWriter(fileWriter)) { writer =>
  for (line <- newContent)
    writer.write(line)
    writer.write('\n')
}
