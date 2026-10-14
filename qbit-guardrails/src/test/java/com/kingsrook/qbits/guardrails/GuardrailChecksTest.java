/*
 * Copyright 2026 QRunIO, LLC
 * SPDX-License-Identifier: Apache-2.0
 */
package com.kingsrook.qbits.guardrails;


import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Behavioral regression tests, using real bytecode fixtures without QQQ jars.
 *******************************************************************************/
class GuardrailChecksTest
{
   @TempDir
   Path temporary;


   /***************************************************************************
    ** Own-package detection is stable and can be explicitly overridden.
    ***************************************************************************/
   @Test
   void detectsOwnPackage()
   {
      var names = List.of("com.kingsrook.qbits.one.A", "com.kingsrook.qbits.two.B", "com.kingsrook.qbits.two.C");
      assertEquals("two", GuardrailChecks.ownPackage(names, null));
      assertEquals("one", GuardrailChecks.ownPackage(names, "com.kingsrook.qbits.one.."));
      assertEquals("one", GuardrailChecks.ownPackage(names, "one"));
      assertEquals("", GuardrailChecks.ownPackage(List.of("org.example.App"), null));
   }


   /***************************************************************************
    ** Only foreign implementation packages are forbidden.
    ***************************************************************************/
   @Test
   void packageBoundaries()
   {
      var allowed = List.of("com.kingsrook.qbits.other.api..");
      for(String name : List.of("java.lang.String", "javax.sql.DataSource", "jdk.foo.Bar", "org.example.Lib", "com.kingsrook.qqq.Core", "com.kingsrook.qbits.own.Internal", "com.kingsrook.qbits.other.api.Model", "com.kingsrook.qbits.other.api.nested.Model"))
      {
         assertTrue(GuardrailChecks.allowed(name, "own", allowed), name);
      }
      assertFalse(GuardrailChecks.allowed("com.kingsrook.qbits.other.Internal", "own", allowed));
      assertFalse(GuardrailChecks.allowed("com.kingsrook.qbits.other.apiInternal.Model", "own", allowed));
      assertFalse(GuardrailChecks.allowed("com.kingsrook.qbits.ownExtra.Model", "own", allowed));
   }


   /***************************************************************************
    ** Assistant consumers can use the published SPI without exposing internals.
    ***************************************************************************/
   @Test
   void defaultAssistantSpiPackages() throws Exception
   {
      String oldPackages = System.getProperty("qbit.guardrails.allowedApiPackages");
      String oldClasspath = System.getProperty("surefire.test.class.path");
      try
      {
         System.clearProperty("qbit.guardrails.allowedApiPackages");
         System.setProperty("surefire.test.class.path", temporary.toString());
         var allowed = new GuardrailProject().apiPackages();
         assertTrue(GuardrailChecks.allowed("com.kingsrook.qbits.assistant.spi.ModelRequest", "own", allowed));
         for(String name : List.of("com.kingsrook.qbits.assistant.spiInternal.X", "com.kingsrook.qbits.assistant.engine.ModelPricing", "com.kingsrook.qbits.assistant.provider.X", "com.kingsrook.qbits.assistant.api.X"))
         {
            assertFalse(GuardrailChecks.allowed(name, "own", allowed), name);
         }
      }
      finally
      {
         restore("qbit.guardrails.allowedApiPackages", oldPackages);
         restore("surefire.test.class.path", oldClasspath);
      }
   }


   /***************************************************************************
    ** The existing API artifact wildcard already covers the assistant provider.
    ***************************************************************************/
   @Test
   void assistantProviderApiArtifact()
   {
      assertTrue(GuardrailProject.artifactMatches("com.kingsrook.qbits:*-api", "com.kingsrook.qbits:qbit-assistant-provider-api"));
      assertFalse(GuardrailProject.artifactMatches("com.kingsrook.qbits:*-api", "com.kingsrook.qbits:qbit-assistant"));
   }


   /***************************************************************************
    ** Architecture analysis uses actual fields and method calls from bytecode.
    ***************************************************************************/
   @Test
   void bytecodeDependencies()
   {
      var classes = new ClassFileImporter().importClasses(com.kingsrook.qbits.own.Fixture.class);
      var violations = GuardrailChecks.dependencies(classes, "own", List.of("com.kingsrook.qbits.other.api"));
      assertTrue(violations.stream().anyMatch(v -> v.contains("other.Internal")));
      assertTrue(violations.stream().noneMatch(v -> v.contains("api.Model")));
   }


   /***************************************************************************
    ** Ignore comments/chars; preserve lines for reflection strings/text blocks.
    ***************************************************************************/
   @Test
   void scansLiterals()
   {
      String source = "// \"com.kingsrook.qbits.other.Comment\"\n"
         + "char quote = '\"'; /* \"com.kingsrook.qbits.other.Comment\" */\n"
         + "Class.forName(\"com.kingsrook.qbits.other.Internal\");\n"
         + "String okay = \"com.kingsrook.qbits.other.api.Model\";\n"
         + "String own = \"com.kingsrook.qbits.own.Local\";\n"
         + "String block = \"\"\"\n   com.kingsrook.qbits.other.Block\n\"\"\";";
      var violations = GuardrailChecks.literalViolations("Example.java", source, "own", List.of("com.kingsrook.qbits.other.api"));
      assertEquals(2, violations.size());
      assertTrue(violations.get(0).startsWith("Example.java:3:"));
      assertTrue(violations.get(1).startsWith("Example.java:7:"));
   }


   /***************************************************************************
    ** Escaped quotes cannot terminate a string or create a comment.
    ***************************************************************************/
   @Test
   void escapedStrings()
   {
      var literals = GuardrailChecks.literals("String x = \"quote \\\" // still a string\"; String y = \"next\";");
      assertEquals(2, literals.size());
      assertEquals("next", literals.get(1).value());
   }


   /***************************************************************************
    ** Contracts are required only when the extension type is referenced.
    ***************************************************************************/
   @Test
   void detectsExtensionContract()
   {
      var importer = new ClassFileImporter();
      var main = importer.importClasses(com.kingsrook.qbits.own.ExtensionFixture.class);
      var tests = importer.importClasses(com.kingsrook.qbits.own.ContractFixture.class);
      var empty = importer.importPaths(List.of());
      assertEquals(2, GuardrailChecks.extensionViolations(main, empty, false).size());
      assertEquals(1, GuardrailChecks.extensionViolations(main, tests, false).size());
      assertEquals(1, GuardrailChecks.extensionViolations(main, empty, true).size());
      assertTrue(GuardrailChecks.extensionViolations(main, tests, true).isEmpty());
      assertTrue(GuardrailChecks.extensionViolations(empty, empty, false).isEmpty());
   }


   /***************************************************************************
    ** A Javalin registration differs from merely referring to Javalin types.
    ***************************************************************************/
   @Test
   void detectsRouteRegistration()
   {
      var classes = new ClassFileImporter().importClasses(com.kingsrook.qbits.own.RouteFixture.class, com.kingsrook.qbits.own.Fixture.class);
      assertTrue(GuardrailChecks.registersRoutes(classes.get(com.kingsrook.qbits.own.RouteFixture.class)));
      assertFalse(GuardrailChecks.registersRoutes(classes.get(com.kingsrook.qbits.own.Fixture.class)));
   }


   /***************************************************************************
    ** Removal milestones follow Maven ordering, including numeric RC suffixes.
    ***************************************************************************/
   @Test
   void comparesVersions()
   {
      assertFalse(GuardrailChecks.expired("4.1.0-RC.5", "4.1.0-RC.6"));
      assertTrue(GuardrailChecks.expired("4.1.0-RC.6", "4.1.0-RC.6"));
      assertTrue(GuardrailChecks.expired("4.1.0-RC.10", "4.1.0-RC.6"));
      assertTrue(GuardrailChecks.expired("4.1.0", "4.1.0-RC.6"));
      assertFalse(GuardrailChecks.expired("4.1.0-RC.6", "4.1.0"));
   }


   /***************************************************************************
    ** Accept any owner/repo and detect expiration with a useful location.
    ***************************************************************************/
   @Test
   void validMarkers()
   {
      String source = "// STAND-IN(core=QRun-IO/qbit-x#42, removeBy=4.1.0-RC.6)";
      assertTrue(GuardrailChecks.standIns("A.java", source, "4.1.0-RC.5").violations().isEmpty());
      assertTrue(GuardrailChecks.standIns("A.java", source, "4.1.0").violations().getFirst().startsWith("A.java:1:"));
      assertEquals(1, GuardrailChecks.standIns("A.java", source, null).warnings().size());
   }


   /***************************************************************************
    ** Missing milestones and malformed issue/version syntax cannot pass.
    ***************************************************************************/
   @Test
   void malformedMarkers()
   {
      for(String marker : List.of("STAND-IN(core=QRun-IO/qqq#1)", "STAND-IN(core=qqq#1, removeBy=4.1.0)", "STAND-IN(core=QRun-IO/qqq#0, removeBy=4.1.0)", "STAND-IN(core=QRun-IO/qqq#1, removeBy=soon)", "STAND-IN(core=QRun-IO/qqq#1, removeBy=4.1.0", "stand-in(core=QRun-IO/qqq#1, removeBy=4.1.0)"))
      {
         assertEquals(1, GuardrailChecks.standIns("A.java", marker, "4.0.0").violations().size(), marker);
      }
   }


   /***************************************************************************
    ** Scan all markers per line; prose stand-ins remain advisory.
    ***************************************************************************/
   @Test
   void multipleMarkersAndWarnings()
   {
      var result = GuardrailChecks.standIns("A.java", "STAND-IN(core=QRun-IO/qqq#1, removeBy=4.0.0) STAND-IN(core=QRun-IO/qqq#2)\n// temporary StandIn\n// stand-in until core catches up", "4.1.0");
      assertEquals(2, result.violations().size());
      assertEquals(2, result.warnings().size());
   }


   /***************************************************************************
    ** Only allowed API artifacts contribute automatic API package exemptions.
    ***************************************************************************/
   @Test
   void discoversApiArtifactPackages() throws Exception
   {
      Path jar = temporary.resolve("fixture-api.jar");
      try(var output = new JarOutputStream(Files.newOutputStream(jar)))
      {
         output.putNextEntry(new JarEntry("META-INF/maven/com.kingsrook.qbits/fixture-api/pom.properties"));
         output.write("groupId=com.kingsrook.qbits\nartifactId=fixture-api\nversion=1.0.0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
         output.closeEntry();
         output.putNextEntry(new JarEntry("com/kingsrook/qbits/fixture/api/Model.class"));
         output.closeEntry();
         output.putNextEntry(new JarEntry("com/kingsrook/qbits/fixture/internal/Secret.class"));
         output.closeEntry();
      }
      String oldClasspath = System.getProperty("surefire.test.class.path");
      String oldAllow = System.getProperty("qbit.guardrails.allowedQbitArtifacts");
      try
      {
         System.setProperty("surefire.test.class.path", jar.toString());
         System.setProperty("qbit.guardrails.allowedQbitArtifacts", "com.kingsrook.qbits:*-api");
         assertTrue(new GuardrailProject().apiPackages().contains("com.kingsrook.qbits.fixture.api"));
         assertFalse(new GuardrailProject().apiPackages().contains("com.kingsrook.qbits.fixture.internal"));
         System.setProperty("qbit.guardrails.allowedQbitArtifacts", "com.kingsrook.qbits:qbit-object-storage");
         assertFalse(new GuardrailProject().apiPackages().contains("com.kingsrook.qbits.fixture.api"));
      }
      finally
      {
         restore("surefire.test.class.path", oldClasspath);
         restore("qbit.guardrails.allowedQbitArtifacts", oldAllow);
      }
   }


   /***************************************************************************
    ** Default reports pass, enforcement fails, and advisory warnings never fail.
    ***************************************************************************/
   @Test
   void reportVersusEnforce()
   {
      String old = System.getProperty("qbit.guardrails.enforce");
      PrintStream original = System.out;
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try
      {
         System.setOut(new PrintStream(bytes));
         System.setProperty("qbit.guardrails.enforce", "false");
         GuardrailProject.report("G1", List.of("bad reference"), List.of());
         assertTrue(bytes.toString().contains("G1 FAIL (report-only): 1 violations"));
         System.setProperty("qbit.guardrails.enforce", "true");
         assertThrows(AssertionError.class, () -> GuardrailProject.report("G1", List.of("bad reference"), List.of()));
         GuardrailProject.report("G3", List.of(), List.of("raw /qqq/v1 routes"));
      }
      finally { System.setOut(original); restore("qbit.guardrails.enforce", old); }
   }


   /***************************************************************************
    ** Missing and empty outputs do not throw import errors.
    ***************************************************************************/
   @Test
   void emptyOutputs() throws Exception
   {
      GuardrailProject project = new GuardrailProject();
      assertTrue(project.classes(temporary.resolve("missing")).isEmpty());
      assertTrue(project.classes(Files.createDirectory(temporary.resolve("empty"))).isEmpty());
   }


   /***************************************************************************
    ** Raw route warnings identify the provider's source line and path.
    ***************************************************************************/
   @Test
   void rawRouteDiagnostics() throws Exception
   {
      Path source = temporary.resolve("src/main/java/com/kingsrook/qbits/own/RouteFixture.java");
      Files.createDirectories(source.getParent());
      Files.writeString(source, "// documentation \"/qqq/v1/not-a-route\"\napp.get(\"/qqq/v1/fixture\");");
      String old = System.getProperty("qbit.guardrails.basedir");
      try
      {
         System.setProperty("qbit.guardrails.basedir", temporary.toString());
         var classes = new ClassFileImporter().importClasses(com.kingsrook.qbits.own.RouteFixture.class);
         var warnings = new GuardrailProject().rawRoutes(classes);
         assertEquals(1, warnings.size());
         assertTrue(warnings.getFirst().endsWith(":2: raw /qqq/v1 routes: /qqq/v1/fixture"));
      }
      finally { restore("qbit.guardrails.basedir", old); }
   }


   /***************************************************************************
    ** Archives require YAML in the exact contract directory, not test resources.
    ***************************************************************************/
   @Test
   void sourceArchiveContracts() throws Exception
   {
      String old = System.getProperty("qbit.guardrails.basedir");
      try
      {
         System.setProperty("qbit.guardrails.basedir", temporary.toString());
         GuardrailProject project = new GuardrailProject();
         assertFalse(project.hasCommittedYaml());
         Path directory = temporary.resolve("src/main/resources/openapi/qqq-v1");
         Files.createDirectories(directory);
         Files.writeString(directory.resolve("extension.json"), "{}");
         assertFalse(project.hasCommittedYaml());
         Files.writeString(directory.resolve("extension.yaml"), "openapi: 3.0.3");
         assertTrue(project.hasCommittedYaml());
      }
      finally { restore("qbit.guardrails.basedir", old); }
   }


   /***************************************************************************
    ** Standalone consumers can supply a version if no core resource exists.
    ***************************************************************************/
   @Test
   void coreVersionFallback() throws Exception
   {
      String old = System.getProperty("qqq.version");
      try
      {
         System.setProperty("qqq.version", "4.1.0-RC.6");
         assertEquals("4.1.0-RC.6", new GuardrailProject().coreVersion());
      }
      finally { restore("qqq.version", old); }
   }


   /***************************************************************************
    ** Restore process properties so tests do not contaminate each other.
    ***************************************************************************/
   private static void restore(String key, String value)
   {
      if(value == null) { System.clearProperty(key); }
      else { System.setProperty(key, value); }
   }
}
