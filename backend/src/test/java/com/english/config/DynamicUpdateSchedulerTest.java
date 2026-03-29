package com.english.config;

import com.english.entity.UpdateConfig;
import com.english.service.UpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicUpdateSchedulerTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private UpdateService updateService;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private DynamicUpdateScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DynamicUpdateScheduler(taskScheduler, updateService);
    }

    @Test
    @DisplayName("init swallows update config loading failures")
    void init_whenConfigUnavailable_doesNotThrow() {
        when(updateService.getConfig()).thenThrow(new RuntimeException("missing table"));

        assertDoesNotThrow(() -> scheduler.init());

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    @Test
    @DisplayName("reschedule registers cron task when update is supported and enabled")
    void reschedule_whenSupportedAndEnabled_schedulesTask() {
        UpdateConfig config = new UpdateConfig();
        config.setEnabled(true);
        config.setCronExpr("0 15 2 * * ?");
        config.setGitBranch("main");

        when(updateService.isSupported()).thenReturn(true);
        doReturn(scheduledFuture).when(taskScheduler)
                .schedule(any(Runnable.class), any(CronTrigger.class));

        scheduler.reschedule(config);

        ArgumentCaptor<CronTrigger> triggerCaptor = ArgumentCaptor.forClass(CronTrigger.class);
        verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
        assertEquals("0 15 2 * * ?", triggerCaptor.getValue().getExpression());
    }

    @Test
    @DisplayName("reschedule skips scheduling when config is null")
    void reschedule_whenConfigNull_doesNotSchedule() {
        when(updateService.isSupported()).thenReturn(true);

        scheduler.reschedule(null);

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    @Test
    @DisplayName("reschedule cancels previous task when update becomes unsupported")
    void reschedule_whenUnsupported_cancelsPreviousTask() {
        UpdateConfig config = new UpdateConfig();
        config.setEnabled(true);
        config.setCronExpr("0 0 3 * * ?");

        when(scheduledFuture.isCancelled()).thenReturn(false);
        when(updateService.isSupported()).thenReturn(false);
        when(updateService.getUnsupportedReason()).thenReturn("disabled");
        ReflectionTestUtils.setField(scheduler, "currentFuture", scheduledFuture);

        scheduler.reschedule(config);

        verify(scheduledFuture).cancel(false);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
    }
}
