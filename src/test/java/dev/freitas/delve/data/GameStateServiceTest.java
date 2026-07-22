package dev.freitas.delve.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameStateServiceTest {

    private PlayerSaveService playerSaveService;
    private ObjectMapper objectMapper;
    private GameStateService gameStateService;

    @BeforeEach
    void setUp() {
        playerSaveService = mock(PlayerSaveService.class);
        objectMapper = new ObjectMapper();
        gameStateService = new GameStateService(playerSaveService, objectMapper);
    }

    @Test
    void userLockReturnsSameLockForSameUser() {
        long userId = 1001L;
        ReentrantLock lock1 = gameStateService.userLock(userId);
        ReentrantLock lock2 = gameStateService.userLock(userId);

        assertThat(lock1).isNotNull();
        assertThat(lock2).isSameAs(lock1);
    }

    @Test
    void userLockReturnsDifferentLockForDifferentUsers() {
        ReentrantLock lockUser1 = gameStateService.userLock(1001L);
        ReentrantLock lockUser2 = gameStateService.userLock(1002L);

        assertThat(lockUser1).isNotSameAs(lockUser2);
    }

    @Test
    void withUserLockExecutesActionHoldingLock() {
        long userId = 2002L;
        String result = gameStateService.withUserLock(userId, () -> {
            ReentrantLock lock = gameStateService.userLock(userId);
            assertThat(lock.isHeldByCurrentThread()).isTrue();
            return "success";
        });

        assertThat(result).isEqualTo("success");
    }
}
