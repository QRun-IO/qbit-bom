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
 ** G4: temporary stand-ins have a precise removal milestone.
 *******************************************************************************/
public class StandInGuardrailTest
{
   /***************************************************************************
    ** Source comments are intentional inputs; both production and tests count.
    ***************************************************************************/
   @Test
   void expiry() throws Exception
   {
      GuardrailProject project = new GuardrailProject();
      if(project.classes(project.output).isEmpty()) { GuardrailProject.empty("G4"); return; }
      List<String> violations = new ArrayList<>();
      List<String> warnings = new ArrayList<>();
      String coreVersion = project.coreVersion();
      for(String tree : List.of("src/main/java", "src/test/java"))
      {
         for(var source : project.sources(tree))
         {
            var result = GuardrailChecks.standIns(project.basedir.relativize(source).toString(), Files.readString(source), coreVersion);
            violations.addAll(result.violations());
            warnings.addAll(result.warnings());
         }
      }
      GuardrailProject.report("G4", violations, warnings);
   }
}
