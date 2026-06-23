package com.example.common.performance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DevDbHotPathPrewarmServiceTest {

    @Mock
    private DataSource primaryDataSource;

    @Mock
    private DataSource stadiumDataSource;

    @Mock
    private Connection firstConnection;

    @Mock
    private Connection secondConnection;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSet resultSet;

    @Test
    void runPrewarmSkipsDatabaseAccessWhenDisabled() throws Exception {
        DevDbHotPathPrewarmService service = disabledService();

        service.runPrewarm();

        verify(primaryDataSource, never()).getConnection();
        verify(stadiumDataSource, never()).getConnection();
    }

    @Test
    void runPrewarmWarmsConfiguredConnectionsWithBoundedReadOnlyQueries() throws Exception {
        when(primaryDataSource.getConnection()).thenReturn(firstConnection, secondConnection);
        when(stadiumDataSource.getConnection()).thenReturn(firstConnection, secondConnection);
        when(firstConnection.prepareStatement(anyString())).thenReturn(statement);
        when(secondConnection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);

        DevDbHotPathPrewarmService service = enabledService(2);

        service.runPrewarm();

        verify(primaryDataSource, org.mockito.Mockito.times(2)).getConnection();
        verify(stadiumDataSource, org.mockito.Mockito.times(2)).getConnection();
        verify(firstConnection, org.mockito.Mockito.times(2)).setReadOnly(true);
        verify(secondConnection, org.mockito.Mockito.times(2)).setReadOnly(true);
        verify(statement, org.mockito.Mockito.times(6)).setQueryTimeout(30);
        verify(statement, org.mockito.Mockito.times(2)).setString(1, "latency-prewarm@example.invalid");
        verify(statement, org.mockito.Mockito.times(2)).setDate(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.any());
        verify(statement, org.mockito.Mockito.times(2)).setDate(org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.any());
        verify(statement, org.mockito.Mockito.times(2)).setInt(1, 2026);
        verify(statement, org.mockito.Mockito.times(6)).executeQuery();
        verify(firstConnection, org.mockito.Mockito.times(2)).close();
        verify(secondConnection, org.mockito.Mockito.times(2)).close();
    }

    @Test
    void runPrewarmDoesNotFailStartupWhenAQueryFailsAndStillClosesConnection() throws Exception {
        when(primaryDataSource.getConnection()).thenReturn(firstConnection);
        when(firstConnection.prepareStatement(anyString())).thenReturn(statement);
        doThrow(new SQLException("planner timeout")).when(statement).executeQuery();
        when(stadiumDataSource.getConnection()).thenThrow(new SQLException("pool unavailable"));

        DevDbHotPathPrewarmService service = enabledService(1);

        assertThatCode(service::runPrewarm).doesNotThrowAnyException();
        verify(firstConnection).close();
    }

    @Test
    void runPrewarmKeepsConnectionsOpenUntilAllConfiguredConnectionsAreBorrowed() throws Exception {
        when(primaryDataSource.getConnection()).thenReturn(firstConnection, secondConnection);
        when(stadiumDataSource.getConnection()).thenReturn(firstConnection, secondConnection);
        when(firstConnection.prepareStatement(anyString())).thenReturn(statement);
        when(secondConnection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);

        DevDbHotPathPrewarmService service = enabledService(2);

        service.runPrewarm();

        InOrder order = inOrder(primaryDataSource, firstConnection, secondConnection);
        order.verify(primaryDataSource).getConnection();
        order.verify(primaryDataSource).getConnection();
        order.verify(firstConnection).close();
        order.verify(secondConnection).close();
    }

    private DevDbHotPathPrewarmService disabledService() {
        return new DevDbHotPathPrewarmService(
                primaryDataSource,
                stadiumDataSource,
                false,
                1,
                30000,
                "latency-prewarm@example.invalid",
                LocalDate.of(2026, 6, 18),
                LocalDate.of(2026, 6, 24),
                2026);
    }

    private DevDbHotPathPrewarmService enabledService(int connections) {
        return new DevDbHotPathPrewarmService(
                primaryDataSource,
                stadiumDataSource,
                true,
                connections,
                30000,
                "latency-prewarm@example.invalid",
                LocalDate.of(2026, 6, 18),
                LocalDate.of(2026, 6, 24),
                2026);
    }
}
