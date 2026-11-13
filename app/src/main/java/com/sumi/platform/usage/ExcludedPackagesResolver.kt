package com.sumi.platform.usage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.sumi.core.common.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Resolves the packages that must never be counted as app usage
 * (ARCHITECTURE.md §15, rules in `:domain` `UsageExclusions`).
 *
 * Device-specific set:
 *  - **Sumi itself** — opening Sumi to check your usage is not screen time.
 *  - **The default launcher** — the home screen is not an app.
 *  - **The current input method** — the keyboard is not an app.
 *
 * The launcher and the IME can change while the app runs, so the set is resolved per
 * collection run rather than cached for the process lifetime. Resolution never throws:
 * an unresolvable launcher or IME simply means that package is not excluded.
 */
interface ExcludedPackagesResolver {
    /** Packages to exclude on this device right now. */
    suspend fun resolve(): Set<String>
}

@Singleton
class DeviceExcludedPackagesResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) : ExcludedPackagesResolver {

    override suspend fun resolve(): Set<String> = withContext(dispatchers.io) {
        val excluded = linkedSetOf<String>()
        // Sumi itself.
        excluded += context.packageName
        defaultLauncherPackage()?.let { excluded += it }
        currentInputMethodPackage()?.let { excluded += it }
        excluded
    }

    /** The package that owns the default HOME activity. */
    private fun defaultLauncherPackage(): String? = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(
                home,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        }
        resolved?.activityInfo?.packageName?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    /** The package of the current input method, e.g. `com.google.android.inputmethod.latin`. */
    private fun currentInputMethodPackage(): String? = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}
