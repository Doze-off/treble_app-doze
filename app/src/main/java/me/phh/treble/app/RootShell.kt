package me.phh.treble.app

object RootShell {
    private const val TAG = "DualWifi_Root"

    private val SH = "/system/bin/sh"
    private const val SU = "/system/bin/su"

    private val mountMasterFlag: Boolean by lazy {
        try {
            val proc = ProcessBuilder(SH, "-c", "$SU -M -c id 2>&1").start()
            val out = proc.inputStream.bufferedReader().readText()
            val exit = proc.waitFor()
            val supported = exit == 0 && out.contains("uid=0")
            DualWifiLogger.i(TAG, "su mount-master (-M) supported: $supported")
            supported
        } catch (e: Exception) {
            DualWifiLogger.w(TAG, "Could not probe su -M: ${e.message}")
            false
        }
    }

    fun isRootAvailable(): Boolean {
        val res = run("id")
        val available = res.isSuccess && (res.output.contains("uid=0") || res.output.contains("root"))
        DualWifiLogger.i(TAG, "Root availability check: available=$available (exitCode=${res.exitCode}, out=${res.output.lines().firstOrNull()})")
        return available
    }

    fun run(cmd: String): CommandResult {
        val startTime = System.currentTimeMillis()
        DualWifiLogger.d(TAG, "CMD >>> $cmd")

        val suCmd = if (mountMasterFlag) "$SU -M -c" else "$SU -c"
        val shellArgs = listOf(SH, "-c", "$suCmd '${cmd.replace("'", "'\\''")}'")

        return try {
            val process = ProcessBuilder(shellArgs)
                .redirectErrorStream(true)
                .start()

            val reader = process.inputStream.bufferedReader()
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }

            val exitCode = process.waitFor()
            val duration = System.currentTimeMillis() - startTime
            val trimmedOut = output.toString().trim()

            if (exitCode == 0) {
                DualWifiLogger.d(TAG, "CMD <<< [OK ${duration}ms]")
                if (trimmedOut.isNotEmpty()) {
                    trimmedOut.lines().take(5).forEach { l ->
                        DualWifiLogger.v(TAG, "  | $l")
                    }
                }
            } else {
                DualWifiLogger.w(TAG, "CMD <<< [FAIL exit=$exitCode ${duration}ms] out: ${trimmedOut.take(200)}")
            }

            CommandResult(exitCode, trimmedOut)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            DualWifiLogger.e(TAG, "Exception executing '$cmd' after ${duration}ms: ${e.message}", e)
            CommandResult(-1, e.message ?: "Execution exception")
        }
    }

    data class CommandResult(val exitCode: Int, val output: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }
}
