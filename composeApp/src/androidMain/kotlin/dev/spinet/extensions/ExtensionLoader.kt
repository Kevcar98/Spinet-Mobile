package dev.spinet.extensions

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dalvik.system.PathClassLoader
import dev.spinet.extension.ApiVersion
import dev.spinet.extension.MusicSource
import dev.spinet.extension.SourceFactory

object ExtensionLoader {
    private const val PACKAGE_PREFIX = "dev.spinet.extension."
    private const val META_FACTORY = "spinet.extension.factory"
    private const val META_API = "spinet.extension.api"

    data class Loaded(
        val packageName: String,
        val label: String,
        val versionName: String,
        val sources: List<MusicSource>
    )

    data class Rejected(val packageName: String, val reason: String)

    data class Result(val loaded: List<Loaded>, val rejected: List<Rejected>)

    fun loadAll(context: Context): Result {
        val pm = context.packageManager
        val loaded = mutableListOf<Loaded>()
        val rejected = mutableListOf<Rejected>()

        val candidates = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            .filter { it.packageName.startsWith(PACKAGE_PREFIX) }

        for (pkg in candidates) {
            val info = pkg.applicationInfo ?: continue
            val meta = info.metaData
            val factoryName = meta?.getString(META_FACTORY)
            if (factoryName.isNullOrBlank()) {
                rejected += Rejected(pkg.packageName, "no $META_FACTORY in the manifest")
                continue
            }

            val declaredApi = meta.getInt(META_API, -1)
            if (declaredApi < 0) {
                rejected += Rejected(pkg.packageName, "no $META_API in the manifest")
                continue
            }
            if (declaredApi > ApiVersion.CURRENT) {
                rejected += Rejected(
                    pkg.packageName,
                    "needs Spinet with extension api $declaredApi; this one has ${ApiVersion.CURRENT}"
                )
                continue
            }

            val result = runCatching { instantiate(info, factoryName) }
            result.fold(
                onSuccess = { sources ->
                    loaded += Loaded(
                        packageName = pkg.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        versionName = pkg.versionName.orEmpty(),
                        sources = sources
                    )
                },
                onFailure = { e ->

                    rejected += Rejected(
                        pkg.packageName,
                        e.message ?: e::class.simpleName ?: "failed to load"
                    )
                }
            )
        }
        return Result(loaded, rejected)
    }

    private fun instantiate(info: ApplicationInfo, factoryName: String): List<MusicSource> {
        val classLoader = ExtensionClassLoader(
            info.sourceDir,
            info.nativeLibraryDir,
            ExtensionLoader::class.java.classLoader!!
        )
        val factory = classLoader.loadClass(factoryName)
            .getDeclaredConstructor()
            .newInstance() as SourceFactory
        return factory.create()
    }
}

internal class ExtensionClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    private val app: ClassLoader
) : PathClassLoader(dexPath, librarySearchPath, app) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        findLoadedClass(name)?.let { return it }
        if (isShared(name)) return app.loadClass(name)
        return try {
            findClass(name)
        } catch (e: ClassNotFoundException) {
            app.loadClass(name)
        }
    }

    companion object {
        private val SHARED_PREFIXES = listOf(
            "java.", "javax.", "android.", "androidx.", "dalvik.", "kotlin.", "kotlinx."
        )
        private const val CONTRACT_PACKAGE = "dev.spinet.extension."

        fun isShared(name: String): Boolean =
            SHARED_PREFIXES.any { name.startsWith(it) } ||
                (name.startsWith(CONTRACT_PACKAGE) && '.' !in name.removePrefix(CONTRACT_PACKAGE))
    }
}
