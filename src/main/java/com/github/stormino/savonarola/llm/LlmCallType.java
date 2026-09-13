package com.github.stormino.savonarola.llm;

/** SPEC 13 reports token spend per call type, so the seam carries what a call was for. */
public enum LlmCallType { JUDGMENT, PROFILE, RULEBOOK }
