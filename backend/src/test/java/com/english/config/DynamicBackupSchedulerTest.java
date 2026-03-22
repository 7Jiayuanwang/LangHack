package com.english.config;

import com.english.entity.BackupConfig;
import com.english.service.BackupConfigService;
import com.english.service.BackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DynamicBackupScheduler.
 *
 * Dependencies:
 * - TaskScheduler taskScheduler (constructor param: backupTaskScheduler)
 * - BackupConfigService backupConfigService (constructor-injected)
 * - BackupService backupService (constructor-injected, @Lazy)
 *
 * Key implementation facts:
 * - init() is @PostConstruct, calls reschedule()
 * - reschedule() is synchronized
 * - reschedule() cancels existing scheduledTask before scheduling new one
 * - When autoBackupEnabled=false, cancels existing and returns without scheduling
 * - When cron is null/empty, defaults to "0 0 2 * * ?"
 * - Catches exceptions from backupConfigService.getConfig() without crashing
 */
@ExtendWith(MockitoExtension.class)
class DynamicBackupSchedulerTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private BackupConfigService backupConfigService;

    @Mock
    private BackupService backupService;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private DynamicBackupScheduler scheduler;

    @BeforeEach
    void setUp() {
        // Manually construct to avoid @PostConstruct triggering in test
        scheduler = new DynamicBackupScheduler(taskScheduler, backupConfigService, backupService);
        // Clear the scheduledTask field (it's null by default, but explicit for clarity)
        ReflectionTestUtils.setField(scheduler, "scheduledTask", null);
    }

    // ==================== reschedule tests ====================

    @Nested
    @DisplayName("reschedule 重新调度备份任务")
    class RescheduleTests {

        @Test
        @DisplayName("autoBackupEnabled为true时使用正确的cron调度任务")
        void reschedule_enabledWithCron_schedulesTask() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setAutoBackupEnabled(true);
            config.setBackupCron("0 30 3 * * ?");
            when(backupConfigService.getConfig()).thenReturn(config);
            doReturn(scheduledFuture).when(taskScheduler)
                    .schedule(any(Runnable.class), any(CronTrigger.class));

            // Act
            scheduler.reschedule();

            // Assert
            ArgumentCaptor<CronTrigger> triggerCaptor = ArgumentCaptor.forClass(CronTrigger.class);
            verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
            assertEquals("0 30 3 * * ?", triggerCaptor.getValue().getExpression());
        }

        @Test
        @DisplayName("autoBackupEnabled为true且cron为null时使用默认cron")
        void reschedule_enabledWithNullCron_usesDefaultCron() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setAutoBackupEnabled(true);
            config.setBackupCron(null);
            when(backupConfigService.getConfig()).thenReturn(config);
            doReturn(scheduledFuture).when(taskScheduler)
                    .schedule(any(Runnable.class), any(CronTrigger.class));

            // Act
            scheduler.reschedule();

            // Assert
            ArgumentCaptor<CronTrigger> triggerCaptor = ArgumentCaptor.forClass(CronTrigger.class);
            verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
            assertEquals("0 0 2 * * ?", triggerCaptor.getValue().getExpression());
        }

        @Test
        @DisplayName("autoBackupEnabled为true且cron为空字符串时使用默认cron")
        void reschedule_enabledWithEmptyCron_usesDefaultCron() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setAutoBackupEnabled(true);
            config.setBackupCron("");
            when(backupConfigService.getConfig()).thenReturn(config);
            doReturn(scheduledFuture).when(taskScheduler)
                    .schedule(any(Runnable.class), any(CronTrigger.class));

            // Act
            scheduler.reschedule();

            // Assert
            ArgumentCaptor<CronTrigger> triggerCaptor = ArgumentCaptor.forClass(CronTrigger.class);
            verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
            assertEquals("0 0 2 * * ?", triggerCaptor.getValue().getExpression());
        }

        @Test
        @DisplayName("autoBackupEnabled为false时取消已有任务且不调度新任务")
        void reschedule_disabled_cancelsExistingAndDoesNotSchedule() {
            // Arrange - set up an existing scheduled task
            when(scheduledFuture.isCancelled()).thenReturn(false);
            ReflectionTestUtils.setField(scheduler, "scheduledTask", scheduledFuture);

            BackupConfig config = new BackupConfig();
            config.setAutoBackupEnabled(false);
            when(backupConfigService.getConfig()).thenReturn(config);

            // Act
            scheduler.reschedule();

            // Assert
            verify(scheduledFuture).cancel(false);
            verify(taskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
        }

        @Test
        @DisplayName("autoBackupEnabled为null时不调度新任务")
        void reschedule_autoBackupEnabledNull_doesNotSchedule() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setAutoBackupEnabled(null);
            when(backupConfigService.getConfig()).thenReturn(config);

            // Act
            scheduler.reschedule();

            // Assert
            verify(taskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
        }

        @Test
        @DisplayName("调度新任务前取消之前的已有任务")
        void reschedule_cancelsPreviousTaskBeforeSchedulingNew() {
            // Arrange - set up an existing scheduled task
            when(scheduledFuture.isCancelled()).thenReturn(false);
            ReflectionTestUtils.setField(scheduler, "scheduledTask", scheduledFuture);

            BackupConfig config = new BackupConfig();
            config.setAutoBackupEnabled(true);
            config.setBackupCron("0 0 4 * * ?");
            when(backupConfigService.getConfig()).thenReturn(config);

            @SuppressWarnings("unchecked")
            ScheduledFuture<?> newFuture = mock(ScheduledFuture.class);
            doReturn(newFuture).when(taskScheduler)
                    .schedule(any(Runnable.class), any(CronTrigger.class));

            // Act
            scheduler.reschedule();

            // Assert - verify cancel happened, then schedule happened
            verify(scheduledFuture).cancel(false);
            verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        }

        @Test
        @DisplayName("已取消的任务不会重复调用cancel")
        void reschedule_alreadyCancelledTask_doesNotCancelAgain() {
            // Arrange - existing task is already cancelled
            when(scheduledFuture.isCancelled()).thenReturn(true);
            ReflectionTestUtils.setField(scheduler, "scheduledTask", scheduledFuture);

            BackupConfig config = new BackupConfig();
            config.setAutoBackupEnabled(true);
            config.setBackupCron("0 0 5 * * ?");
            when(backupConfigService.getConfig()).thenReturn(config);
            when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                    .thenReturn(mock(ScheduledFuture.class));

            // Act
            scheduler.reschedule();

            // Assert - cancel should NOT be called because isCancelled() is true
            verify(scheduledFuture, never()).cancel(anyBoolean());
        }

        @Test
        @DisplayName("configService抛出异常时不崩溃且不调度任务")
        void reschedule_configServiceThrows_logsErrorAndDoesNotCrash() {
            // Arrange
            when(backupConfigService.getConfig()).thenThrow(new RuntimeException("DB error"));

            // Act - should not throw
            assertDoesNotThrow(() -> scheduler.reschedule());

            // Assert
            verify(taskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
        }

        @Test
        @DisplayName("无已有任务时直接调度新任务")
        void reschedule_noPreviousTask_schedulesDirectly() {
            // Arrange - scheduledTask is null (default)
            BackupConfig config = new BackupConfig();
            config.setAutoBackupEnabled(true);
            config.setBackupCron("0 15 1 * * ?");
            when(backupConfigService.getConfig()).thenReturn(config);
            doReturn(scheduledFuture).when(taskScheduler)
                    .schedule(any(Runnable.class), any(CronTrigger.class));

            // Act
            scheduler.reschedule();

            // Assert
            verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        }

        @Test
        @DisplayName("调度的Runnable执行时调用backupService.executeScheduledBackup")
        void reschedule_scheduledRunnable_callsExecuteScheduledBackup() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setAutoBackupEnabled(true);
            config.setBackupCron("0 0 2 * * ?");
            when(backupConfigService.getConfig()).thenReturn(config);

            ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
            doReturn(scheduledFuture).when(taskScheduler)
                    .schedule(runnableCaptor.capture(), any(CronTrigger.class));

            // Act
            scheduler.reschedule();

            // Execute the captured runnable
            runnableCaptor.getValue().run();

            // Assert
            verify(backupService).executeScheduledBackup();
        }

        @Test
        @DisplayName("调度的Runnable执行时backupService抛异常不崩溃")
        void reschedule_scheduledRunnable_backupServiceThrows_doesNotCrash() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setAutoBackupEnabled(true);
            config.setBackupCron("0 0 2 * * ?");
            when(backupConfigService.getConfig()).thenReturn(config);

            ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
            doReturn(scheduledFuture).when(taskScheduler)
                    .schedule(runnableCaptor.capture(), any(CronTrigger.class));

            doThrow(new RuntimeException("Backup failed")).when(backupService).executeScheduledBackup();

            // Act
            scheduler.reschedule();

            // Execute the captured runnable - should not throw
            assertDoesNotThrow(() -> runnableCaptor.getValue().run());
        }
    }

    // ==================== init tests ====================

    @Nested
    @DisplayName("init 初始化调度（@PostConstruct）")
    class InitTests {

        @Test
        @DisplayName("init方法调用reschedule进行初始调度")
        void init_callsReschedule() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setAutoBackupEnabled(true);
            config.setBackupCron("0 0 2 * * ?");
            when(backupConfigService.getConfig()).thenReturn(config);
            doReturn(scheduledFuture).when(taskScheduler)
                    .schedule(any(Runnable.class), any(CronTrigger.class));

            // Act
            scheduler.init();

            // Assert - init delegates to reschedule, which should schedule the task
            verify(backupConfigService).getConfig();
            verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        }

        @Test
        @DisplayName("init方法在configService异常时不崩溃")
        void init_configServiceThrows_doesNotCrash() {
            // Arrange
            when(backupConfigService.getConfig()).thenThrow(new RuntimeException("Startup error"));

            // Act & Assert
            assertDoesNotThrow(() -> scheduler.init());
        }
    }
}
