import React, { useEffect, useState } from "react"
import WM, { Worker, WorkInfo, isPeriodicWorker, isQueuedWorker } from "./workManager"

type HookWorkInfo<V> = WorkInfo<V> & { id: string }

const initialHookWorkInfo: HookWorkInfo<any> = {
    id: undefined as any,
    state: "unknown",
    attemptCount: 0,
    value: undefined,
}

/**
 * React hook to enqueue some payload and watch to the work's state
 * @param work the worker name and payload to be enqueued
 */
export function useEnqueue<P,V>(work: { worker: string, payload: P }): HookWorkInfo<V> {
    
    const [info, setInfo] = useState<HookWorkInfo<V>>(initialHookWorkInfo)

    useEffect(() => {
        let unsubscriber: () => void
        WM.enqueue(work).then((id) => unsubscriber = WM.addListener(id, (info: WorkInfo<V>) => setInfo({ ...info, id })))
        return () => unsubscriber && unsubscriber()
    })

    return info

}

/**
 * React hook to register a worker
 * If the worker is periodic, the hook will return the work's state
 * If the worker is queued, the hook will return another hook to enqueue work to this worker.
 * @param worker the worker information to be registered
 * @param lifecycle Use only with periodic worker, if this is setted to detached, the worker will continue alive after component unmounts.
 */
export function useWorker<P,V,T extends "periodic"|"queued">(
    worker: Worker<P,V,T>,
    lifecycle?: "attached"|"detached"
): T extends "periodic" ? HookWorkInfo<null> : (payload: P) => HookWorkInfo<V> {

    const [periodicWorkerState, setPeriodicWorkerState] = useState<HookWorkInfo<null>>(initialHookWorkInfo)

    useEffect(() => {
        if(isPeriodicWorker(worker)) {
            let unsubscriber: () => void
            
            WM.setWorker(worker)
                .then((id) => {
                    const listener = WM.addListener<null>(id, (info) => setPeriodicWorkerState({ ...info, id }))
                    unsubscriber = () => {
                        listener()
                        lifecycle!=="detached" && WM.cancel(id)
                    }
                })

            return () => unsubscriber && unsubscriber()
        }
        else WM.setWorker(worker)
    })

    if(isPeriodicWorker(worker)) return periodicWorkerState as any

    return ((payload: P) => useEnqueue<P,V>({ worker: worker.name, payload })) as any

}