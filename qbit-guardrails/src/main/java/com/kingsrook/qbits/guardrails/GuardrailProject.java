/*
 * Copyright 2026 QRunIO, LLC
 * SPDX-License-Identifier: Apache-2.0
 */
package com.kingsrook.qbits.guardrails;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;


/*******************************************************************************
 ** Read only the consuming project's outputs and source trees.
 *******************************************************************************/
final class GuardrailProject
{
   final Path basedir = Path.of(System.getProperty("qbit.guardrails.basedir", System.getProperty("user.dir")));
   final Path output = Path.of(System.getProperty("qbit.guardrails.outputDirectory", basedir.resolve("target/classes").toString()));
   final Path testOutput = Path.of(System.getProperty("qbit.guardrails.testOutputDirectory", basedir.resolve("target/test-classes").toString()));


   /***************************************************************************
    ** Avoid resolving the entire QQQ/third-party graph just to inspect targets.
    ***************************************************************************/
   JavaClasses classes(Path directory)
   {
      return ArchConfiguration.get().withThreadLocalScope(configuration ->
      {
         configuration.setResolveMissingDependenciesFromClassPath(false);
         return new ClassFileImporter().importPaths(Files.isDirectory(directory) ? List.of(directory) : List.of());
      });
   }


   /***************************************************************************
    ** Enumerate sources deterministically and close the directory stream.
    ***************************************************************************/
   List<Path> sources(String tree) throws IOException
   {
      Path directory = basedir.resolve(tree);
      if(!Files.isDirectory(directory)) { return List.of(); }
      try(var paths = Files.walk(directory))
      {
         return paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).sorted().toList();
      }
   }


   /***************************************************************************
    ** Accept an explicit ownPackage override for unusual or mixed layouts.
    ***************************************************************************/
   String own(JavaClasses classes)
   {
      return GuardrailChecks.ownPackage(classes.stream().map(c -> c.getName()).toList(), System.getProperty("qbit.guardrails.ownPackage"));
   }


   /***************************************************************************
    ** Only API packages actually supplied by allowed *-api artifacts are added.
    ** Reading jar metadata avoids any dependency on QQQ or Maven project APIs.
    ***************************************************************************/
   Set<String> apiPackages() throws IOException
   {
      Set<String> packages = new TreeSet<>(csv(System.getProperty("qbit.guardrails.allowedApiPackages", "com.kingsrook.qbits.objectstorage.api,com.kingsrook.qbits.email.api")));
      List<String> allowedArtifacts = csv(System.getProperty("qbit.guardrails.allowedQbitArtifacts", "com.kingsrook.qbits:qbit-object-storage,com.kingsrook.qbits:qbit-email,com.kingsrook.qbits:*-api"));
      String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
      for(String entry : classpath.split(Pattern.quote(File.pathSeparator)))
      {
         Path path = Path.of(entry);
         if(!entry.endsWith(".jar") || !Files.isRegularFile(path)) { continue; }
         try(JarFile jar = new JarFile(path.toFile()))
         {
            boolean allowedApi = false;
            for(var metadata : jar.stream().filter(e -> e.getName().startsWith("META-INF/maven/com.kingsrook.qbits/") && e.getName().endsWith("/pom.properties")).toList())
            {
               Properties properties = new Properties();
               try(InputStream input = jar.getInputStream(metadata)) { properties.load(input); }
               String artifact = properties.getProperty("artifactId", "");
               String coordinate = properties.getProperty("groupId", "") + ":" + artifact;
               allowedApi |= artifact.endsWith("-api") && allowedArtifacts.stream().anyMatch(pattern -> artifactMatches(pattern, coordinate));
            }
            if(allowedApi)
            {
               jar.stream().map(e -> e.getName().replace('/', '.'))
                  .filter(name -> name.matches("com\\.kingsrook\\.qbits\\.[^.]+\\.api\\..+\\.class"))
                  .map(name -> GuardrailChecks.QBIT_PREFIX + GuardrailChecks.qbitName(name) + ".api").forEach(packages::add);
            }
         }
      }
      return packages;
   }


   /***************************************************************************
    ** Allow-list entries use Maven groupId:artifactId patterns with * wildcards.
    ***************************************************************************/
   static boolean artifactMatches(String pattern, String coordinate)
   {
      String regex = Arrays.stream(pattern.split("\\*", -1)).map(Pattern::quote).collect(java.util.stream.Collectors.joining(".*"));
      return coordinate.matches(regex);
   }


   /***************************************************************************
    ** Share comma-separated property parsing with the parent POM convention.
    ***************************************************************************/
   static List<String> csv(String text)
   {
      return Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
   }


   /***************************************************************************
    ** Require a source YAML; in a checkout also verify it exists in HEAD.
    ** Source archives lack Git metadata and are checked by source presence.
    ***************************************************************************/
   boolean hasCommittedYaml() throws IOException, InterruptedException
   {
      Path directory = basedir.resolve("src/main/resources/openapi/qqq-v1");
      if(!Files.isDirectory(directory)) { return false; }
      List<Path> yaml;
      try(var files = Files.list(directory))
      {
         yaml = files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".yaml")).toList();
      }
      if(yaml.isEmpty()) { return false; }
      Path checkout = basedir.toAbsolutePath();
      while(checkout != null && !Files.exists(checkout.resolve(".git"))) { checkout = checkout.getParent(); }
      if(checkout == null) { return true; }
      // An archive may live inside another checkout's ignored scratch directory.
      if(!committed(checkout, basedir.resolve("pom.xml"))) { return true; }
      for(Path path : yaml)
      {
         if(committed(checkout, path)) { return true; }
      }
      return false;
   }


   /***************************************************************************
    ** Check HEAD rather than the index: staged files are not yet committed.
    ***************************************************************************/
   private static boolean committed(Path checkout, Path path) throws IOException, InterruptedException
   {
      String relative = checkout.relativize(path.toAbsolutePath()).toString().replace('\\', '/');
      Process process = new ProcessBuilder("git", "-C", checkout.toString(), "cat-file", "-e", "HEAD:" + relative)
         .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      return process.waitFor() == 0;
   }


   /***************************************************************************
    ** Prefer the actual backend artifact's version over a caller's fallback.
    ***************************************************************************/
   String coreVersion() throws IOException
   {
      try(InputStream input = getClass().getClassLoader().getResourceAsStream("META-INF/maven/com.kingsrook.qqq/qqq-backend-core/pom.properties"))
      {
         if(input != null)
         {
            Properties properties = new Properties();
            properties.load(input);
            String version = properties.getProperty("version");
            if(version != null && !version.isBlank()) { return version; }
         }
      }
      return System.getProperty("qqq.version");
   }


   /***************************************************************************
    ** Correlate a route registration's class with its Java source file.
    ***************************************************************************/
   List<String> rawRoutes(JavaClasses classes) throws IOException
   {
      Set<String> providers = new TreeSet<>();
      classes.stream().filter(GuardrailChecks::registersRoutes).forEach(c ->
         providers.add(c.getName().split("\\$", 2)[0].replace('.', '/') + ".java"));
      List<String> warnings = new ArrayList<>();
      Path sourceRoot = basedir.resolve("src/main/java");
      for(String provider : providers)
      {
         Path source = sourceRoot.resolve(provider);
         if(!Files.isRegularFile(source)) { continue; }
         GuardrailChecks.literals(Files.readString(source, StandardCharsets.UTF_8)).stream()
            .filter(l -> l.value().startsWith("/qqq/v1/"))
            .map(l -> basedir.relativize(source) + ":" + l.line() + ": raw /qqq/v1 routes: " + l.value()).forEach(warnings::add);
      }
      return warnings;
   }


   /***************************************************************************
    ** Each test reports before asserting; report-only tests always pass.
    ***************************************************************************/
   static void report(String rule, List<String> violations, List<String> warnings)
   {
      boolean enforce = Boolean.getBoolean("qbit.guardrails.enforce");
      String status = violations.isEmpty() ? "PASS" : "FAIL" + (enforce ? " (enforced)" : " (report-only)");
      System.out.println("[qbit-guardrails] " + rule + " " + status + ": " + violations.size() + " violations");
      violations.forEach(v -> System.out.println("[qbit-guardrails] " + rule + " " + v));
      warnings.forEach(w -> System.out.println("[qbit-guardrails] " + rule + " WARN: " + w));
      if(enforce && !violations.isEmpty())
      {
         throw new AssertionError(rule + ": " + String.join("\n", violations));
      }
   }


   /***************************************************************************
    ** Missing/empty main output is normal for aggregator and POM projects.
    ***************************************************************************/
   static void empty(String rule)
   {
      System.out.println("[qbit-guardrails] " + rule + " PASS: no main classes; nothing to check");
   }
}
