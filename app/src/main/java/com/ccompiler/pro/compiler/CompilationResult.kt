package com.ccompiler.pro.compiler

data class Diagnostic(val line: Int, val column: Int, val severity: String, val message: String)

data class CompilationResult(
    val success: Boolean,
    val output: String,
    val diagnostics: List<Diagnostic>,
    val durationMs: Long,
    val timedOut: Boolean = false,
    val cancelled: Boolean = false
)

data class ExecResult(
    val exitCode: Int,
    val durationMs: Long,
    val timedOut: Boolean = false,
    val memoryKilled: Boolean = false,
    val cancelled: Boolean = false
)
