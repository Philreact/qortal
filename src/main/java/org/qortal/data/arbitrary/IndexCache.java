package org.qortal.data.arbitrary;

import org.qortal.list.ResourceList;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class IndexCache {

    public static final IndexCache SINGLETON = new IndexCache();
    private ConcurrentHashMap<String, List<ArbitraryDataIndexDetail>> indicesByTerm = new ConcurrentHashMap<>();

    public static IndexCache getInstance() {
        return SINGLETON;
    }

    public ConcurrentHashMap<String, List<ArbitraryDataIndexDetail>> getIndicesByTerm() {
        return indicesByTerm;
    }
}
