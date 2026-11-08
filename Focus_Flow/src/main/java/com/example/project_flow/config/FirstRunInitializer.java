package com.example.project_flow.config;

import com.example.project_flow.entity.Priority;
import com.example.project_flow.entity.Task;
import com.example.project_flow.entity.WorkspaceSettings;
import com.example.project_flow.repository.TaskRepository;
import com.example.project_flow.repository.WorkspaceSettingsRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Creates the settings row and the original starter tasks once. The settings row marks the first
 * run as done, so starter tasks the user deletes never come back.
 */
@Component
@Order(1)
public class FirstRunInitializer implements ApplicationRunner {

    private static final List<Map.Entry<String, Priority>> STARTER_TASKS = List.of(
            Map.entry("Finish the FOCUS FLOW home screen", Priority.HIGH),
            Map.entry("Organize the project feature list", Priority.HIGH),
            Map.entry("Verify the Spring Boot startup process", Priority.MEDIUM),
            Map.entry("Write down what I learned today", Priority.LOW));

    private final WorkspaceSettingsRepository settings;
    private final TaskRepository tasks;
    private final Clock clock;

    public FirstRunInitializer(WorkspaceSettingsRepository settings, TaskRepository tasks, Clock clock) {
        this.settings = settings;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (settings.existsById(WorkspaceSettings.SINGLETON_ID)) {
            return;
        }
        settings.save(new WorkspaceSettings());
        if (tasks.count() == 0) {
            for (Map.Entry<String, Priority> starter : STARTER_TASKS) {
                Task task = new Task(LocalDateTime.now(clock));
                task.update(starter.getKey(), null, starter.getValue(), null, null, null);
                tasks.save(task);
            }
        }
    }
}
