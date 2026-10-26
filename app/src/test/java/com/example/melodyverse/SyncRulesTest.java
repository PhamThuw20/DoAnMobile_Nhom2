package com.example.melodyverse;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Kiểm thử quy tắc đồng bộ offline: chuyển trạng thái sync_status khi thích / bỏ thích
 * (cũng áp dụng cho lưu bài và theo dõi) và dữ liệu mẫu nạp lên Firebase.
 */
public class SyncRulesTest {

    @Test
    public void likeWhenNoRow_becomesPending() {
        assertEquals(SyncStatus.PENDING, SyncStatus.nextRelationState(null, true));
    }

    @Test
    public void unlikeSyncedRow_waitsForServerDelete() {
        assertEquals(SyncStatus.PENDING_DELETE, SyncStatus.nextRelationState(SyncStatus.SYNCED, false));
    }

    @Test
    public void unlikePendingRow_isJustRemovedLocally() {
        // Chưa từng lên Firebase thì không cần gửi lệnh xóa
        assertEquals(SyncStatus.REMOVE_ROW, SyncStatus.nextRelationState(SyncStatus.PENDING, false));
    }

    @Test
    public void relikeBeforeDeleteWasSent_restoresSynced() {
        // Bỏ thích rồi thích lại lúc offline: bản ghi trên Firebase vẫn còn, không cần gửi gì
        assertEquals(SyncStatus.SYNCED, SyncStatus.nextRelationState(SyncStatus.PENDING_DELETE, true));
    }

    @Test
    public void repeatedActions_doNothing() {
        assertEquals(SyncStatus.NO_CHANGE, SyncStatus.nextRelationState(SyncStatus.SYNCED, true));
        assertEquals(SyncStatus.NO_CHANGE, SyncStatus.nextRelationState(SyncStatus.PENDING, true));
        assertEquals(SyncStatus.NO_CHANGE, SyncStatus.nextRelationState(null, false));
        assertEquals(SyncStatus.NO_CHANGE, SyncStatus.nextRelationState(SyncStatus.PENDING_DELETE, false));
    }

    @Test
    public void demoAccounts_mapBothWays() {
        assertEquals(SeedData.DEMO_USER_EMAIL, SeedData.demoEmail("MinhThu"));
        assertEquals(SeedData.DEMO_ADMIN, SeedData.demoUsername("ADMIN@melodyverse.vn"));
        assertTrue(SeedData.isDemoCredential(SeedData.DEMO_USER_EMAIL, SeedData.DEMO_USER_PASSWORD));
        assertFalse(SeedData.isDemoCredential(SeedData.DEMO_USER_EMAIL, "sai-mat-khau"));
        assertNull(SeedData.demoEmail("nguoila"));
    }
}
