//> using lib "org.scalameta::munit::0.7.29"

package docpkg

class DocumentationPackagerSuite extends munit.FunSuite:
  test("run() returns") {
    assertEquals(DocumentationPackager.run(), ())
  }