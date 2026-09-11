package com.antoniopennino.thecut.repositories

import android.content.Context
import android.net.Uri
import com.antoniopennino.thecut.models.EditRecipe
import com.antoniopennino.thecut.models.VideoProject
import com.antoniopennino.thecut.utils.ProjectSerializer
import com.antoniopennino.thecut.utils.ProjectStorageManager
import com.antoniopennino.thecut.utils.SavedProjectItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader

/**
 * Clean Architecture Repository interface for project persistence,
 * serialization, and loading.
 */
interface ProjectRepository {
    suspend fun saveProject(context: Context, project: VideoProject, customName: String? = null): File?
    suspend fun loadProjectFromUri(context: Context, uri: Uri): EditRecipe?
    suspend fun getSavedProjects(context: Context): List<SavedProjectItem>
    fun serialize(recipe: EditRecipe): String
    fun deserialize(reader: InputStreamReader): EditRecipe
}

class DefaultProjectRepository : ProjectRepository {

    override suspend fun saveProject(context: Context, project: VideoProject, customName: String?): File? = withContext(Dispatchers.IO) {
        ProjectStorageManager.saveProject(context, project, customName)
    }

    override suspend fun loadProjectFromUri(context: Context, uri: Uri): EditRecipe? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ProjectSerializer.deserialize(InputStreamReader(stream))
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getSavedProjects(context: Context): List<SavedProjectItem> = withContext(Dispatchers.IO) {
        ProjectStorageManager.getSavedProjects(context)
    }

    override fun serialize(recipe: EditRecipe): String {
        return ProjectSerializer.serialize(recipe)
    }

    override fun deserialize(reader: InputStreamReader): EditRecipe {
        return ProjectSerializer.deserialize(reader)
    }
}
