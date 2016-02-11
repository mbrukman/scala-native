package native
package compiler

import java.io.File
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.{DirectoryFileFilter, RegexFileFilter}
import scala.collection.mutable
import native.nir._
import native.nir.serialization._

final class Loader(paths: Seq[String]) {
  private lazy val pathmap: Map[Global, String] =
    paths.flatMap { path =>
      val base = new File(path)
      val baseabs = base.getAbsolutePath()
      val files =
        FileUtils.listFiles(
          base,
          new RegexFileFilter("(.*)\\.nir"),
          DirectoryFileFilter.DIRECTORY).toArray.toIterator
      files.map { case file: File =>
        val fileabs = file.getAbsolutePath()
        val relpath = fileabs.replace(baseabs + "/", "")
        val (tag, rel) =
          if (relpath.endsWith(".class.nir"))
            ("c", relpath.replace(".class.nir", ""))
          // TODO: it might be better to strip $ in plugin
          else if (relpath.endsWith("$.module.nir"))
            ("m", relpath.replace("$.module.nir", ""))
          else if (relpath.endsWith(".module.nir"))
            ("m", relpath.replace(".module.nir", ""))
          else if (relpath.endsWith(".interface.nir"))
            ("i", relpath.replace(".interface.nir", ""))
          else
            throw new Exception(s"can't parse file kind $relpath")
        val parts = rel.split("/").toSeq
        val name = Global(parts.mkString("."), tag)
        (name -> fileabs)
      }.toSeq
    }.toMap

  def load(entry: Global): Seq[Defn] = {
    val loaded = mutable.Set.empty[Global]
    var deps   = mutable.Stack[Global](entry)
    var defns  = mutable.UnrolledBuffer.empty[Defn]

    while (deps.nonEmpty) {
      val dep = deps.pop()
      if (!loaded.contains(dep) && !dep.isIntrinsic) {
        val (newdeps, newdefns) = deserializeBinaryFile(pathmap(dep))
        deps.pushAll(newdeps)
        defns ++= newdefns
        loaded += dep
      }
    }

    defns.toSeq
  }
}