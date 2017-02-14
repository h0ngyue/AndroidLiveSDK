package com.render_view;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public final class LockStore {

    private static volatile Map<String, Date> locks = new HashMap<String, Date>();

    private LockStore() {
    }

    public synchronized static Boolean getLock(String lockName) {
        Boolean locked = false;

        if ("".equals(lockName)) {
            throw new RuntimeException("Lock name can't be empty");
        }

        Date lockDate = locks.get(lockName);
        if (lockDate == null) {
            locks.put(lockName, new Date());
            locked = true;
        }

        return locked;
    }

    public synchronized static void releaseLock(String lockName) {
        if ("".equals(lockName)) {
            throw new RuntimeException("Lock name can't be empty");
        }

        Date lockDate = locks.get(lockName);
        if (lockDate != null) {
            locks.remove(lockName);
        }
    }

    public synchronized static Date getLockDate(String lockName) {
        if ("".equals(lockName)) {
            throw new RuntimeException("Lock name can't be empty");
        }

        Date lockDate = locks.get(lockName);
        return lockDate;
    }
}