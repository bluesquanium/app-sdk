package com.samsung.healthcare.kit.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.samsung.healthcare.kit.app.setting.SettingPreference
import com.samsung.healthcare.kit.common.TaskGenerator
import com.samsung.healthcare.kit.external.network.ResearchPlatformAdapter
import com.samsung.healthcare.kit.external.network.TaskClient
import com.samsung.healthcare.kit.repository.TaskRepository
import com.samsung.healthcare.kit.task.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDateTime

class TaskViewModel(
    private val taskRepository: TaskRepository,
    private val settingPreference: SettingPreference,
) : ViewModel() {
    private var targetDateTime: LocalDateTime = LocalDateTime.now()

    private val _activeTasks = MutableStateFlow(TasksState(emptyList()))
    val activeTasks: StateFlow<TasksState> = _activeTasks

    private val _todayTasks = MutableStateFlow(TasksState(emptyList()))
    val todayTasks: StateFlow<TasksState> = _todayTasks

    private val _completedTasks = MutableStateFlow(TasksState(emptyList()))
    val completedTasks: StateFlow<TasksState> = _completedTasks

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                taskRepository.getActiveDailyTask(targetDateTime)
                    .collect { tasks ->
                        _activeTasks.value = TasksState(tasks)
                    }
            }
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                taskRepository.getUpcomingDailyTask(targetDateTime)
                    .collect { tasks ->
                        _todayTasks.value = TasksState(tasks)
                    }
            }
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                taskRepository.getCompletedDailyTasks(targetDateTime.toLocalDate())
                    .collect { tasks ->
                        _completedTasks.value = TasksState(tasks)
                    }
            }
        }
    }

    fun done(task: Task) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                taskRepository.updateResult(task)
            }
        }
    }

    fun syncTasks() {
        val networkClient: TaskClient = ResearchPlatformAdapter.getInstance()
        FirebaseAuth.getInstance().currentUser?.getIdToken(false)
            ?.addOnSuccessListener { result ->
                result.token?.let { idToken ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val lastSyncTime = settingPreference.taskSyncTime.first()

                            val endTime =
                                LocalDateTime.now(Clock.systemUTC()).toString()

                            val taskSpecs = networkClient.getTasks(
                                idToken,
                                LocalDateTime.parse(lastSyncTime)
                            )

                            taskSpecs.map {
                                taskRepository.insertAll(TaskGenerator.generate(it))
                            }

                            settingPreference.setTaskSyncTime(endTime)
                        } catch (e: Exception) {
                            Log.d(ResearchPlatformAdapter::class.simpleName, "fail to download task data")
                            e.printStackTrace()
                        }
                    }
                }
            }?.addOnFailureListener {
                Log.d(ResearchPlatformAdapter::class.simpleName, "fail to get id token")
            }
    }

    data class TasksState(val tasks: List<Task>)
}
