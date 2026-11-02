package com.example.media.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.media.entity.PhysicalMediaLockStripe;
import com.example.media.repository.PhysicalMediaLockStripeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PhysicalMediaCreationLockTest {
    @Test
    void multiObjectLocksAreDeduplicatedAndTakenInStripeOrder() {
        var repository = mock(PhysicalMediaLockStripeRepository.class);
        when(repository.findForWrite(1)).thenReturn(Optional.of(mock(PhysicalMediaLockStripe.class)));
        when(repository.findForWrite(2)).thenReturn(Optional.of(mock(PhysicalMediaLockStripe.class)));
        new PhysicalMediaCreationLock(repository).lockAll(List.of(
                "02" + "0".repeat(62), "01" + "0".repeat(62), "42" + "0".repeat(62)));
        var order = inOrder(repository);
        order.verify(repository).findForWrite(1);
        order.verify(repository).findForWrite(2);
        order.verifyNoMoreInteractions();
    }
}
