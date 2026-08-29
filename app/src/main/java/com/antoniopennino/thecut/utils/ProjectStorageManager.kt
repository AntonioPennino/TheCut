package com.antoniopennino.thecut.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.antoniopennino.thecut.models.EditRecipe
import com.antoniopennino.thecut.models.VideoProject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedProjectItem(
    val file: File,
    val title: String,
    val lastModifiedMs: Long,
    val formattedDate: String,
    val recipe: EditRecipe?
)

object ProjectStorageManager {

    private const val TAG = "ProjectStorageManager"
    private const val PROJECTS_DIR_NAME = "TheCut/Projects"

    fun getProjectsDirectory(context: Context): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val projectsDir = File(baseDir, "Projects")
        if (!projectsDir.exists()) {
            projectsDir.mkdirs()
        }
        return projectsDir
    }

    fun getPublicProjectsDirectory(): File {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val dir = File(moviesDir, "TheCut/Projects")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Saves a project recipe automatically with timestamped or named file.
     */
    fun saveProject(context: Context, project: VideoProject, customName: String? = null): File? {
        return try {
            val recipe = EditRecipe.fromVideoProject(project.sourceName, project)
            val json = ProjectSerializer.serialize(recipe)
            val baseName = customName?.takeIf { it.isNotBlank() }
                ?: (project.sourceName.substringBeforeLast(".").ifBlank { "Progetto" })
            val sanitizedName = baseName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val fileName = "${sanitizedName}.cutproj"

            // Save to app internal projects dir
            val targetDir = getProjectsDirectory(context)
            val projectFile = File(targetDir, fileName)
            FileOutputStream(projectFile).use { fos ->
                fos.write(json.toByteArray())
            }

            // Also try to mirror to public directory for user convenience
            try {
                val publicDir = getPublicProjectsDirectory()
                val publicFile = File(publicDir, fileName)
                FileOutputStream(publicFile).use { fos ->
                    fos.write(json.toByteArray())
                }
            } catch (ignored: Exception) {}

            Log.d(TAG, "Project saved successfully to: ${projectFile.absolutePath}")
            projectFile
        } catch (e: Exception) {
            Log.e(TAG, "Error saving project", e)
            null
        }
    }

    /**
     * Scans for all saved .cutproj and .lcprj files.
     */
    fun getSavedProjects(context: Context): List<SavedProjectItem> {
        val resultList = mutableListOf<SavedProjectItem>()
        val seenFileNames = mutableSetOf<String>()
        val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

        val directoriesToScan = listOf(
            getProjectsDirectory(context),
            getPublicProjectsDirectory()
        )

        for (dir in directoriesToScan) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles { file ->
                file.isFile && (file.name.endsWith(".cutproj", ignoreCase = true) || file.name.endsWith(".lcprj", ignoreCase = true))
            } ?: continue

            for (f in files) {
                if (seenFileNames.add(f.name)) {
                    var recipe: EditRecipe? = null
                    try {
                        FileInputStream(f).use { fis ->
                            recipe = ProjectSerializer.deserialize(fis.reader())
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse recipe for ${f.name}", e)
                    }

                    val title = f.nameWithoutExtension.replace("_", " ")
                    val formattedDate = dateFormat.format(Date(f.lastModified()))
                    resultList.add(
                        SavedProjectItem(
                            file = f,
                            title = title,
                            lastModifiedMs = f.lastModified(),
                            formattedDate = formattedDate,
                            recipe = recipe
                        )
                    )
                }
            }
        }

        // Sort descending by last modified
        return resultList.sortedByDescending { it.lastModifiedMs }
    }
}
