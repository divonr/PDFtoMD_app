package com.divonr.pdftomd.data

import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {

    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    suspend fun getProject(id: Long): ProjectEntity? {
        return projectDao.getProjectById(id)
    }

    suspend fun saveProject(project: ProjectEntity): Long {
        return projectDao.insertProject(project)
    }

    suspend fun deleteProject(id: Long) {
        projectDao.deleteProjectById(id)
    }
}
