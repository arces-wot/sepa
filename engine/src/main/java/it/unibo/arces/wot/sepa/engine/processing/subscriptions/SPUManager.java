package it.unibo.arces.wot.sepa.engine.processing.subscriptions;

import it.unibo.arces.wot.sepa.commons.response.UpdateResponse;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

/**
 * SpuManager is a monitor class. It takes care of the SPU collection and it encapsulates filtering algorithms based
 * on the internal structure.
 */
public class SPUManager {
    private HashMap<String,ISPU> spus = new HashMap<>();

    public synchronized void register(ISPU spu){
        spus.put(spu.getUUID(),spu);
    }

    public synchronized void unRegister(String spuID){
        if(!isValidSpuId(spuID)){
            throw new IllegalArgumentException("Unregistering a not existing SPUID: "+ spuID);
        }
        spus.get(spuID).terminate();
        spus.remove(spuID);
    }

    public synchronized boolean isValidSpuId(String id){
        return spus.containsKey(id);
    }

    public synchronized Iterator<ISPU> filter(UpdateResponse response){
        return spus.values().iterator();
    }

    public synchronized Collection<ISPU> getAll(){
        return spus.values();
    }

    public synchronized int size(){
        return spus.values().size();
    }


}
