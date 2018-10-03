// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;

import java.util.logging.Logger;

/**
 * For debugging tenant issues in configserver. Activate by loading component.
 *
 * @author Ulf Lilleengen
 */
public class TenantDebugger implements TreeCacheListener {

    private final TreeCache cache;
    private static final Logger log = Logger.getLogger(TenantDebugger.class.getName());

    public TenantDebugger(Curator curator) throws Exception {
        cache = new TreeCache(curator.framework(), "/config/v2/tenants");
        cache.getListenable().addListener(this);
        cache.start();
    }

    @Override
    public void childEvent(CuratorFramework client, TreeCacheEvent event) throws Exception {
        switch (event.getType()) {
            case NODE_ADDED:
            case NODE_REMOVED:
            case NODE_UPDATED:
                log.log(LogLevel.INFO, event.toString());
                break;
        }
    }

}
