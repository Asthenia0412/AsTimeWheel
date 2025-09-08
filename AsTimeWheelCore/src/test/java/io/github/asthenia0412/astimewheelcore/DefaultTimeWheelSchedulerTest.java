package io.github.asthenia0412.astimewheelcore;


import io.github.asthenia0412.astimewheelcore.core.HashedWheelTimer;
import io.github.asthenia0412.astimewheelcore.core.Timeout;
import io.github.asthenia0412.astimewheelcore.core.TimerTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class DefaultTimeWheelSchedulerTest {

    private HashedWheelTimer mockTimer;
    private DefaultTimeWheelScheduler scheduler;
    private Timeout mockTimeout;

    @BeforeEach
    void setUp() {
        mockTimer = Mockito.mock(HashedWheelTimer.class);
        mockTimeout = Mockito.mock(Timeout.class);
        scheduler = new DefaultTimeWheelScheduler(mockTimer);
    }

    @Test
    void schedule_ShouldRegisterTaskAndReturnId() {
        Runnable task = () -> {};
        when(mockTimer.newTimeout(any(TimerTask.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(mockTimeout);

        String taskId = scheduler.schedule(task, 100, TimeUnit.MILLISECONDS);

        assertNotNull(taskId);
        assertTrue(taskId.startsWith("task-"));
        verify(mockTimer).newTimeout(any(TimerTask.class), eq(100L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void schedule_ShouldRemoveTaskFromMapWhenExecuted() throws Exception {
        Runnable task = () -> {};
        ArgumentCaptor<TimerTask> timerTaskCaptor = ArgumentCaptor.forClass(TimerTask.class);
        when(mockTimer.newTimeout(timerTaskCaptor.capture(), anyLong(), any(TimeUnit.class)))
                .thenReturn(mockTimeout);

        String taskId = scheduler.schedule(task, 100, TimeUnit.MILLISECONDS);

        // Simulate task execution
        timerTaskCaptor.getValue().run(mockTimeout);

        assertFalse(scheduler.cancel(taskId)); // Task should no longer be in map
    }

    @Test
    void scheduleAtFixedRate_ShouldRegisterRepeatingTask() {
        Runnable task = () -> {};
        ArgumentCaptor<TimerTask> timerTaskCaptor = ArgumentCaptor.forClass(TimerTask.class);
        when(mockTimer.newTimeout(timerTaskCaptor.capture(), anyLong(), any(TimeUnit.class)))
                .thenReturn(mockTimeout);

        String taskId = scheduler.scheduleAtFixedRate(task, 100, 200, TimeUnit.MILLISECONDS);

        assertNotNull(taskId);
        verify(mockTimer).newTimeout(any(TimerTask.class), eq(100L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void scheduleAtFixedRate_ShouldRescheduleWhenTaskRuns() throws Exception {
        Runnable task = () -> {};
        ArgumentCaptor<TimerTask> timerTaskCaptor = ArgumentCaptor.forClass(TimerTask.class);
        when(mockTimer.newTimeout(timerTaskCaptor.capture(), anyLong(), any(TimeUnit.class)))
                .thenReturn(mockTimeout);

        String taskId = scheduler.scheduleAtFixedRate(task, 100, 200, TimeUnit.MILLISECONDS);

        // Simulate first execution
        timerTaskCaptor.getValue().run(mockTimeout);

        // Verify it rescheduled itself
        verify(mockTimer, times(2)).newTimeout(any(TimerTask.class), eq(200L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void scheduleAtFixedRate_ShouldNotRescheduleIfTaskWasCancelled() throws Exception {
        Runnable task = () -> {};
        ArgumentCaptor<TimerTask> timerTaskCaptor = ArgumentCaptor.forClass(TimerTask.class);
        when(mockTimer.newTimeout(timerTaskCaptor.capture(), anyLong(), any(TimeUnit.class)))
                .thenReturn(mockTimeout);

        String taskId = scheduler.scheduleAtFixedRate(task, 100, 200, TimeUnit.MILLISECONDS);

        // Cancel the task first
        scheduler.cancel(taskId);

        // Simulate execution (should not reschedule)
        timerTaskCaptor.getValue().run(mockTimeout);

        verify(mockTimer, times(1)).newTimeout(any(TimerTask.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    void cancel_ShouldCancelExistingTask() {
        Runnable task = () -> {};
        when(mockTimer.newTimeout(any(TimerTask.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(mockTimeout);

        String taskId = scheduler.schedule(task, 100, TimeUnit.MILLISECONDS);

        boolean result = scheduler.cancel(taskId);

        assertTrue(result);
        verify(mockTimeout).cancel();
        assertFalse(scheduler.cancel(taskId)); // Should return false for second attempt
    }

    @Test
    void cancel_ShouldReturnFalseForNonExistentTask() {
        boolean result = scheduler.cancel("nonexistent-task");
        assertFalse(result);
    }

    @Test
    void shutdown_ShouldStopTimerAndClearTasks() {
        Runnable task = () -> {};
        when(mockTimer.newTimeout(any(TimerTask.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(mockTimeout);

        String taskId = scheduler.schedule(task, 100, TimeUnit.MILLISECONDS);

        scheduler.shutdown();

        verify(mockTimer).stop();
        assertFalse(scheduler.cancel(taskId)); // Task map should be cleared
    }

    @Test
    void taskIdGenerator_ShouldIncrementForEachTask() {
        Runnable task1 = () -> {};
        Runnable task2 = () -> {};
        when(mockTimer.newTimeout(any(TimerTask.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(mockTimeout);

        String taskId1 = scheduler.schedule(task1, 100, TimeUnit.MILLISECONDS);
        String taskId2 = scheduler.schedule(task2, 100, TimeUnit.MILLISECONDS);

        assertNotEquals(taskId1, taskId2);
        assertTrue(taskId1.compareTo(taskId2) < 0);
    }
}