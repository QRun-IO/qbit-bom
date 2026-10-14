/*
 * Copyright 2026 QRunIO, LLC
 * SPDX-License-Identifier: Apache-2.0
 */
package com.kingsrook.qbits.guardrails;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.apache.maven.artifact.versioning.ComparableVersion;


/*******************************************************************************
 ** Pure checks shared by the inherited tests and this module's fixture tests.
 *******************************************************************************/
final class GuardrailChecks
{
   static final String QBIT_PREFIX = "com.kingsrook.qbits.";
   static final String EXTENSION = "com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtension";
   static final Set<String> CONTRACT_HELPERS = Set.of(
      "com.kingsrook.qqq.middleware.javalin.tools.UiApiContract",
      "com.kingsrook.qqq.middleware.javalin.tools.UiApiContractAssertions");
   private static final Pattern MARKER = Pattern.compile(
      "STAND-IN\\(core=([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+#[1-9][0-9]*), removeBy=([0-9]+\\.[0-9]+\\.[0-9]+(?:[-.][A-Za-z0-9]+)*)\\)");
   private static final Pattern MARKER_START = Pattern.compile("(?i)\\bstand-?in\\s*\\(");
   private static final Pattern UNTAGGED = Pattern.compile("(?i)\\bstand-?in\\b");
   private static final Set<String> ROUTE_METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "path", "routes", "addHandler", "sse", "ws");


   /***************************************************************************
    ** Extract the package segment identifying a qbit, excluding our own harness.
    ***************************************************************************/
   static String qbitName(String className)
   {
      if(!className.startsWith(QBIT_PREFIX))
      {
         return "";
      }
      String tail = className.substring(QBIT_PREFIX.length());
      int dot = tail.indexOf('.');
      return dot < 0 ? "" : tail.substring(0, dot);
   }


   /***************************************************************************
    ** Most frequent fourth segment wins; lexical ordering resolves ties.
    ***************************************************************************/
   static String ownPackage(Collection<String> names, String override)
   {
      if(override != null && !override.isBlank())
      {
         String value = override.trim().replaceFirst("\\.+$", "");
         return value.startsWith(QBIT_PREFIX) ? value.substring(QBIT_PREFIX.length()) : value;
      }
      return names.stream().map(GuardrailChecks::qbitName).filter(s -> !s.isEmpty() && !s.equals("guardrails"))
         .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
         .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed().thenComparing(Map.Entry::getKey))
         .map(Map.Entry::getKey).findFirst().orElse("");
   }


   /***************************************************************************
    ** Exact package boundaries prevent apiInternal from being treated as api.
    ***************************************************************************/
   static boolean allowed(String className, String own, Collection<String> apiPackages)
   {
      if(!className.startsWith(QBIT_PREFIX) || qbitName(className).equals(own))
      {
         return true;
      }
      return apiPackages.stream().map(s -> s.trim().replaceFirst("\\.+$", ""))
         .filter(s -> !s.isEmpty()).anyMatch(s -> className.startsWith(s + "."));
   }


   /***************************************************************************
    ** Inspect bytecode dependencies without linking against any QQQ classes.
    ***************************************************************************/
   static List<String> dependencies(JavaClasses classes, String own, Collection<String> apiPackages)
   {
      return classes.stream().filter(c -> qbitName(c.getName()).equals(own))
         .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
         .filter(d -> !allowed(d.getTargetClass().getName(), own, apiPackages))
         .map(d -> d.getDescription()).distinct().sorted().toList();
   }


   /***************************************************************************
    ** Keep source offsets for useful file:line diagnostics.
    ***************************************************************************/
   record Literal(String value, int line) {}


   /***************************************************************************
    ** Lex Java strings/text blocks, ignoring comments and character literals.
    ** This is intentionally a literal check, not constant-expression evaluation.
    ***************************************************************************/
   static List<Literal> literals(String source)
   {
      List<Literal> result = new ArrayList<>();
      int line = 1;
      for(int i = 0; i < source.length(); )
      {
         char c = source.charAt(i);
         if(c == '\n')
         {
            line++;
            i++;
         }
         else if(source.startsWith("//", i))
         {
            while(i < source.length() && source.charAt(i) != '\n') { i++; }
         }
         else if(source.startsWith("/*", i))
         {
            i += 2;
            while(i < source.length() && !source.startsWith("*/", i))
            {
               if(source.charAt(i++) == '\n') { line++; }
            }
            i = Math.min(i + 2, source.length());
         }
         else if(c == '"' || c == '\'')
         {
            int startLine = line;
            boolean block = source.startsWith("\"\"\"", i);
            String delimiter = block ? "\"\"\"" : String.valueOf(c);
            i += delimiter.length();
            StringBuilder value = new StringBuilder();
            while(i < source.length() && !source.startsWith(delimiter, i))
            {
               char next = source.charAt(i++);
               if(next == '\n') { line++; }
               value.append(next);
               if(next == '\\' && i < source.length())
               {
                  char escaped = source.charAt(i++);
                  if(escaped == '\n') { line++; }
                  value.append(escaped);
               }
            }
            i = Math.min(i + delimiter.length(), source.length());
            if(c == '"')
            {
               String literal = value.toString();
               if(block)
               {
                  String stripped = literal.stripLeading();
                  startLine += (int) literal.substring(0, literal.length() - stripped.length()).chars().filter(ch -> ch == '\n').count();
                  literal = stripped;
               }
               result.add(new Literal(literal, startLine));
            }
         }
         else { i++; }
      }
      return result;
   }


   /***************************************************************************
    ** Reflective class names must obey the same boundary as bytecode references.
    ***************************************************************************/
   static List<String> literalViolations(String file, String source, String own, Collection<String> apiPackages)
   {
      return literals(source).stream().filter(l -> l.value().startsWith(QBIT_PREFIX))
         .filter(l -> !qbitName(l.value()).isEmpty() && !allowed(l.value(), own, apiPackages))
         .map(l -> file + ":" + l.line() + ": " + l.value()).toList();
   }


   /***************************************************************************
    ** Determine whether imported classes reference one of the named contracts.
    ***************************************************************************/
   static boolean references(JavaClasses classes, Set<String> targets)
   {
      return classes.stream().flatMap(c -> c.getDirectDependenciesFromSelf().stream())
         .anyMatch(d -> targets.contains(d.getTargetClass().getName()));
   }


   /***************************************************************************
    ** An extension needs both a source contract and a compiled contract test.
    ***************************************************************************/
   static List<String> extensionViolations(JavaClasses main, JavaClasses tests, boolean hasYaml)
   {
      List<String> violations = new ArrayList<>();
      if(references(main, Set.of(EXTENSION)))
      {
         if(!hasYaml) { violations.add("MiddlewareSpecExtension requires a committed src/main/resources/openapi/qqq-v1/*.yaml"); }
         if(!references(tests, CONTRACT_HELPERS)) { violations.add("No test class references UiApiContract or UiApiContractAssertions"); }
      }
      return violations;
   }


   /***************************************************************************
    ** Raw-route warnings require a registration call, not merely a Context use.
    ***************************************************************************/
   static boolean registersRoutes(JavaClass javaClass)
   {
      return javaClass.getMethodCallsFromSelf().stream().anyMatch(call ->
         call.getTargetOwner().getName().startsWith("io.javalin.") && ROUTE_METHODS.contains(call.getName()));
   }


   /***************************************************************************
    ** Maven ordering handles release candidates and final releases correctly.
    ***************************************************************************/
   static boolean expired(String coreVersion, String removeBy)
   {
      return new ComparableVersion(coreVersion).compareTo(new ComparableVersion(removeBy)) >= 0;
   }


   /***************************************************************************
    ** Separate violations from advisory tagging/version warnings.
    ***************************************************************************/
   record StandIns(List<String> violations, List<String> warnings) {}


   /***************************************************************************
    ** Validate every marker, including multiple markers on the same source line.
    ***************************************************************************/
   static StandIns standIns(String file, String source, String coreVersion)
   {
      List<String> violations = new ArrayList<>();
      List<String> warnings = new ArrayList<>();
      String[] lines = source.split("\\R", -1);
      for(int i = 0; i < lines.length; i++)
      {
         String location = file + ":" + (i + 1) + ": ";
         var start = MARKER_START.matcher(lines[i]);
         boolean hasMarker = false;
         while(start.find())
         {
            hasMarker = true;
            var marker = MARKER.matcher(lines[i]).region(start.start(), lines[i].length());
            if(!marker.lookingAt())
            {
               violations.add(location + "malformed STAND-IN marker: " + lines[i].trim());
            }
            else if(coreVersion == null || coreVersion.isBlank())
            {
               warnings.add(location + "cannot evaluate removeBy=" + marker.group(2) + "; QQQ core version unavailable (set -Dqqq.version)");
            }
            else if(expired(coreVersion, marker.group(2)))
            {
               violations.add(location + marker.group() + " expired at QQQ " + coreVersion);
            }
         }
         if(!hasMarker && UNTAGGED.matcher(lines[i]).find())
         {
            warnings.add(location + "untagged stand-in: " + lines[i].trim());
         }
      }
      return new StandIns(violations, warnings);
   }
}
