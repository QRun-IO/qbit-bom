/*
 * Copyright 2026 QRunIO, LLC
 * SPDX-License-Identifier: Apache-2.0
 */
package com.kingsrook.qbits.own;


/** Bytecode fixture; deliberately minimal and never shipped in the guardrail jar. */
public class RouteFixture { public void register(io.javalin.Javalin app) { app.get("/qqq/v1/fixture"); } }

