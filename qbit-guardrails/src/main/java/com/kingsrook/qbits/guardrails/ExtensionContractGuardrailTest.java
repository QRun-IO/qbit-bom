/*
 * Copyright 2026 QRunIO, LLC
 * SPDX-License-Identifier: Apache-2.0
 */
package com.kingsrook.qbits.guardrails;


import org.junit.jupiter.api.Test;


/*******************************************************************************
 ** G3: middleware extensions ship a committed contract and a contract test.
 *******************************************************************************/
public class ExtensionContractGuardrailTest
{
   /***************************************************************************
    ** Raw routes are advisory even when contract enforcement is enabled.
    ***************************************************************************/
   @Test
   void contract() throws Exception
   {
      GuardrailProject project = new GuardrailProject();
      var classes = project.classes(project.output);
      if(classes.isEmpty()) { GuardrailProject.empty("G3"); return; }
      GuardrailProject.report("G3", GuardrailChecks.extensionViolations(classes, project.classes(project.testOutput), project.hasCommittedYaml()), project.rawRoutes(classes));
   }
}
