package scala.javadoc

import org.scalatest.{ Matchers, WordSpec }
import org.scalatest.matchers.{ Matcher, MatchResult }
import java.net.URLClassLoader
import java.io.File

object SignatureSpec {
  // this should match up against the definition in GenJavaDocPlugin
  val javaKeywords = Set("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
    "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
    "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
    "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
    "throw", "throws", "transient", "try", "void", "volatile", "while")

  // this should match up against the definition in GenJavaDocPlugin
  // with the addition of "$lzycompute", which is special
  val defaultFilteredStrings = Set("$$", "$lzycompute")

  // they can't start with numbers either
  val startsWithNumber = "^\\d".r
}

class SignatureSpec extends WordSpec with Matchers {

  import SignatureSpec._

  "The generated java files" must {

    "contain the same methods and classes as the original Scala files" in {
      val vString = scala.util.Properties.versionNumberString
      val vPath = if (vString.contains("-")) vString else vString.split("\\.").take(2).mkString(".")
      val scalaCL = new URLClassLoader(Array(new File(s"tests/target/scala-$vPath/test-classes/").toURI.toURL), classOf[List[_]].getClassLoader)

      val accProtLvl = Map(1 -> 1, 2 -> 3, 4 -> 2)

      /*
       * This translation is necessary for the evil hack that allows things
       * nested in nested objects to be accepted by JavaDoc: while the emitted
       * Java code compiles, the name mangling of javac and scalac differs for
       * such nestings, which means that it is impossible to express the Scalac
       * generated byte-code in valid Java source. To make things compile
       * nonetheless we just accept that the types here are nonsense, but they
       * are not usable from Java anyway.
       */
      val exception = "(akka.rk.buh.is.it.A\\$[C1D]+\\$)\\$"
      val replacemnt = "$1"

      def check(jc: Class[_]) {
        val sc = scalaCL.loadClass(jc.getName.replaceAll(exception, replacemnt))

        def matchJava(j: Set[String]) = Matcher { (s: Traversable[String]) ⇒
          MatchResult(s == j, s"$s did not match $j (in $jc)", s"$s matched $j (in $jc)")
        }

        val jm = getMethods(jc, filter = false)
        val sm = getMethods(sc, filter = true)
        printIfNotEmpty(sm -- jm, "missing methods:")
        printIfNotEmpty(jm -- sm, "extraneous methods:")
        sm should matchJava(jm)

        val jsub = getClasses(jc, filter = false)
        val ssub = getClasses(sc, filter = true)
        printIfNotEmpty(ssub.keySet -- jsub.keySet, "missing classes:")
        printIfNotEmpty(jsub.keySet -- ssub.keySet, "extraneous classes:")
        ssub.keySet should matchJava(jsub.keySet)

        for (n ← ssub.keys) {
          val js = jsub(n)
          val ss = ssub(n)

          def beEqual[T: Manifest](t: T) = Matcher { (u: T) ⇒ MatchResult(u == t, s"$u was not equal $t (in $n)", s"$u was equal $t (in $n)") }
          def beAtLeast(t: Int) = Matcher { (u: Int) ⇒ MatchResult(u >= t, s"$u was < $t (in $n)", s"$u was >= $t (in $n)") }

          (js.getModifiers & ~15) should beEqual(ss.getModifiers & ~15)
          (ss.getModifiers & 8) should beAtLeast(js.getModifiers & 8) // older Scala versions (2.10) were more STATIC ...
          accProtLvl(js.getModifiers & 7) should beAtLeast(accProtLvl(ss.getModifiers & 7))
          js.getInterfaces.toList.map(_.getName) should beEqual(ss.getInterfaces.toList.map(_.getName))
          js.isInterface should beEqual(ss.isInterface)
          if (!js.isInterface())
            js.getSuperclass.getName should beEqual(ss.getSuperclass.getName)
          check(js)
        }
      }

      def printIfNotEmpty(s: Set[String], msg: String): Unit = if (s.nonEmpty) {
        println(msg)
        s.toList.sorted foreach println
      }

      def getMethods(c: Class[_], filter: Boolean): Set[String] = {
        import language.postfixOps
        c.getDeclaredMethods.filterNot(x ⇒ filter && (defaultFilteredStrings.exists { s => x.getName.contains(s) }
          || javaKeywords.contains(x.getName)
          || startsWithNumber.findFirstIn(x.getName).isDefined))
          .map(_.toGenericString)
          .map(_.replaceAll(exception, replacemnt))
          .toSet
      }

      def getClasses(c: Class[_], filter: Boolean): Map[String, Class[_]] = {
        import language.postfixOps
        c.getDeclaredClasses.collect {
          case x if (!filter || !(x.getName contains "anon")) => x.getName.replaceAll(exception, replacemnt) -> x
        }.toMap
      }

      check(Class.forName("AtTheRoot"))
      check(Class.forName("akka.Main"))
      check(Class.forName("akka.rk.buh.is.it.A"))
      check(Class.forName("akka.rk.buh.is.it.A$"))
      check(Class.forName("akka.rk.buh.is.it.Blarb"))
      check(Class.forName("akka.rk.buh.is.it.Blarb$"))
      check(Class.forName("akka.rk.buh.is.it.X"))
      check(Class.forName("akka.rk.buh.is.it.Y"))
      check(Class.forName("akka.rk.buh.is.it.Z"))
      check(Class.forName("akka.rk.buh.is.it.PPrivate"))
      check(Class.forName("akka.rk.buh.is.it.PPrivate$"))
      check(Class.forName("akka.rk.buh.is.it.Private"))
      check(Class.forName("akka.rk.buh.is.it.Private$"))
      check(Class.forName("akka.rk.buh.is.it.PProtected"))
      check(Class.forName("akka.rk.buh.is.it.PProtected$"))
      check(Class.forName("akka.rk.buh.is.it.PTrait"))
      check(Class.forName("akka.rk.buh.is.it.AnAbstractTypeRef"))
    }

  }

}
