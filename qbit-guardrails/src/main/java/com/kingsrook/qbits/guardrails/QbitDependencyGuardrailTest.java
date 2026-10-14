/*
 * Copyright 2026 QRunIO, LLC
 * SPDX-License-Identifier: Apache-2.0
 */
package com.kingsrook.qbits.guardrails;


import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;


/*******************************************************************************
 ** G1: implementation and reflective dependencies stay inside the owning qbit.
 *******************************************************************************/
public class QbitDependencyGuardrailTest
{
   /***************************************************************************
    ** Keep bytecode and literal results separate in fleet reports.
    ***************************************************************************/
   @Test
   void architecture() throws Exception
   {
      GuardrailProject project = new GuardrailProject();
      var classes = project.classes(project.output);
      if(classes.isEmpty()) { GuardrailProject.empty("G1-arch"); return; }
      String own = project.own(classes);
      GuardrailProject.report("G1-arch", GuardrailChecks.dependencies(classes, own, project.apiPackages()),
         own.isEmpty() ? List.of("No com.kingsrook.qbits.<own> classes found; set qbit.guardrails.ownPackage if needed") : List.of());
   }


   /***************************************************************************
    ** Catch reflective references which bytecode dependency checks cannot see.
    ***************************************************************************/
   @Test
   void literals() throws Exception
   {
      GuardrailProject project = new GuardrailProject();
      var classes = project.classes(project.output);
      if(classes.isEmpty()) { GuardrailProject.empty("G1-literals"); return; }
      String own = project.own(classes);
      var apiPackages = project.apiPackages();
      List<String> violations = new ArrayList<>();
      for(var source : project.sources("src/main/java"))
      {
         violations.addAll(GuardrailChecks.literalViolations(project.basedir.relativize(source).toString(), Files.readString(source), own, apiPackages));
      }
      GuardrailProject.report("G1-literals", violations, List.of());
   }
}
